import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isExecutable

plugins {
    `java-library`
    application
    jacoco
}

enum class OS {
    MAC, WINDOWS, LINUX
}

val currentOS = when {
    System.getProperty("os.name").lowercase().contains("mac") -> OS.MAC
    System.getProperty("os.name").lowercase().contains("windows") -> OS.WINDOWS
    else -> OS.LINUX
}

val isNotarizing = System.getenv("NOTARIZE") != null || gradle.startParameter.taskNames.find { s ->
    s.lowercase().contains("notarize") || s.lowercase().contains("jpackage") || s.lowercase().contains("staple")
} != null

val provisionprofileDir = layout.projectDirectory
    .dir("mac-resources")
    .dir("provisionprofile")
    .dir(
        if (isNotarizing) {
            "notarization"
        } else {
            "app_store"
        }
    )

val macDeveloperApplicationCertName = if (isNotarizing) {
    "Developer ID Application: Tanin Na Nakorn (S6482XAL5E)"
} else {
    "3rd Party Mac Developer Application: Tanin Na Nakorn (S6482XAL5E)"
}

fun getSecret(envKey: String, filePath: String): String? {
    if (System.getenv(envKey) != null) {
        return System.getenv(envKey)
    }

    try {
        return project.file(filePath).readText().trim()
    } catch (e: Exception) {
        return null
    }
}

val appleEmail = getSecret("APPLE_EMAIL", "../secret/APPLE_EMAIL")
val appleAppSpecificPassword = getSecret("APPLE_APP_SPECIFIC_PASSWORD", "../secret/APPLE_APP_SPECIFIC_PASSWORD")
val sentryDsn = getSecret("SENTRY_DSN", "../secret/SENTRY_DSN")
val sentryWebviewDsn = getSecret("SENTRY_WEBVIEW_DSN", "../secret/SENTRY_WEBVIEW_DSN")

val teamId = "S6482XAL5E"
val appName = "Backdoor"
val packageIdentifier = "tanin.backdoor.desktop.macos"
group = "tanin.backdoor.desktop"
version = "1.2"
val internalVersion = "1.2.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()

    sourceSets {
        main {
            resources {
                srcDir(project(":core").sourceSets.main.get().resources)
            }
        }
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.register<Exec>("compileWindowsApi") {
    onlyIf {
        currentOS == OS.WINDOWS
    }
    group = "build"
    description = "Compile C code and output the dll to the resource directory."

    commandLine(
        "gcc",
        "-g",
        "-shared",
        "-o",
        "./src/main/resources/native/WindowsApi.dll",
        "./src/main/c/WindowsApi.c",
        "-lcomdlg32",
        "-lgdi32"
    )
}

tasks.register("compileSwift") {
    onlyIf {
        currentOS == OS.MAC
    }
    group = "build"
    description = "Compile Swift code and output the dylib to the resource directory."

    val inputFile = layout.projectDirectory.file("src/main/swift/MacOsApi.swift").asFile
    val outputFile = layout.projectDirectory.file("src/main/resources/native/libMacOsApi.dylib").asFile

    doLast {
        runCmd(
            "swiftc",
            "-emit-library",
            inputFile.absolutePath,
            "-target",
            "arm64-apple-macos11",
            "-o",
            outputFile.absolutePath,
        )

        codesign(outputFile)
    }
}

tasks.register("writeVersionAndSentryProperties") {
    doLast {
        file("src/main/resources/version.properties").writeText("version=${project.version}")

        val isDev = gradle.startParameter.taskNames.find { s ->
            s.lowercase().contains("run") || s.lowercase().contains("test")
        } != null

        file("src/main/resources/sentry.properties").writeText(
            listOf(
                "dsn=${sentryDsn!!}",
                "webviewDsn=${sentryWebviewDsn!!}",
                "environment=${if (isDev) "Dev" else "Prod"}",
                "send-default-pii=true",
                "release=backdoor-desktop@${internalVersion}"
            ).joinToString("\n")
        )
    }
}

tasks.named<JavaCompile>("compileJava") {
    if (currentOS == OS.MAC) {
        dependsOn("compileSwift")
    } else if (currentOS == OS.WINDOWS) {
        dependsOn("compileWindowsApi")
    }
    dependsOn("writeVersionAndSentryProperties")
    options.compilerArgs.addAll(
        listOf(
            "--add-exports",
            "java.base/sun.security.x509=ALL-UNNAMED",
            "--add-exports",
            "java.base/sun.security.tools.keytool=ALL-UNNAMED",
        )
    )
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report

    reports {
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation(project(":core"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.36.0")
}

tasks.named<Test>("test") {
    jvmArgs = listOf(
        "--add-exports",
        "java.base/sun.security.x509=ALL-UNNAMED",
        "--add-exports",
        "java.base/sun.security.tools.keytool=ALL-UNNAMED",
    )
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging {
        events("started", "passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        showExceptions = true
        showCauses = true
        exceptionFormat = TestExceptionFormat.FULL
    }

}

var mainClassName = "tanin.backdoor.desktop.Main"
application {
    mainClass.set(mainClassName)
    applicationDefaultJvmArgs = buildList {
        if (currentOS == OS.MAC) {
            add("-XstartOnFirstThread")
        }
        add("--add-exports")
        add("java.base/sun.security.x509=ALL-UNNAMED")
        add("--add-exports")
        add("java.base/sun.security.tools.keytool=ALL-UNNAMED")
    }
}

tasks.jar {
    manifest.attributes["Main-Class"] = mainClassName
}


// For CI validation.
tasks.register("printInternalVersion") {
    doLast {
        print(internalVersion)
    }
}

tasks.register("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath).into(layout.buildDirectory.dir("jmods"))
}

tasks.register("copyJar", Copy::class) {
    from(tasks.jar).into(layout.buildDirectory.dir("jmods"))
}

private fun maskSecret(commandLine: String): String {
    return commandLine.split(" ").joinToString(" ") { arg ->
        val sanitized = arg.toDefaultLowerCase()
        if (sanitized.contains("password") || sanitized.contains("secret")) {
            "<REDACTED>"
        } else {
            arg
        }
    }
}

private fun runCmd(currentDir: File, env: Map<String, String>, vararg args: String): String {
    println("Executing command: ${maskSecret(args.joinToString(" "))}")

    val output = StringBuilder()
    val builder = ProcessBuilder(*args)
        .directory(currentDir)

    builder.environment().putAll(env)

    val process = builder.start()
    process.inputStream.bufferedReader().use { reader ->
        reader.lines().forEach {
            println("stdout: $it")
            output.appendLine(it)
        }
    }

    // Print stderr
    process.errorStream.bufferedReader().use { reader ->
        reader.lines().forEach { println("stderr: $it") }
    }

    val retVal = process.waitFor()

    if (retVal != 0) {
        throw IllegalStateException("Command execution failed with return value: $retVal")
    }

    return output.toString()
}

private fun runCmd(currentDir: File, vararg args: String): String {
    return runCmd(currentDir, mapOf(), *args)
}

private fun runCmd(vararg args: String): String {
    return runCmd(layout.projectDirectory.asFile, *args)
}


enum class EntitlementType {
    NoEntitlement,
    MainEntitlement,
    RuntimeEntitlement
}

private fun codesign(file: File, entitlementType: EntitlementType = EntitlementType.NoEntitlement) {
    if (currentOS == OS.MAC) {
        val entitlementArgs = when (entitlementType) {
            EntitlementType.NoEntitlement -> emptyList()
            EntitlementType.MainEntitlement -> listOf("--entitlements", "entitlements.plist")
            EntitlementType.RuntimeEntitlement -> listOf("--entitlements", "runtime-entitlements.plist")
        }

        runCmd(
            *(listOf(
                "codesign",
                "-vvvv",
                "--options",
                "runtime",
            ) + entitlementArgs + listOf(
                "--timestamp",
                "--force",
                "--sign",
                macDeveloperApplicationCertName,
                file.absolutePath,
            )).toTypedArray()
        )
    }
}

private fun isCodesignable(file: File): Boolean {
    if (currentOS == OS.MAC) {
        val excludedExtensions = setOf("so", "a", "xml")
        return file.extension == "dylib" ||
                file.extension == "jnilib" ||
                (!excludedExtensions.contains(file.extension) && file.toPath().isExecutable())
    } else if (currentOS == OS.WINDOWS) {
        return file.extension == "dll"
    } else {
        throw Exception("Unsupported OS: $currentOS")
    }
}


private fun isValidLibFile(file: File): Boolean {
    if (currentOS == OS.MAC) {
        return !(
                file.absolutePath.contains("darwin-x86-64") || // for libjnidispatch.jnilib
                        file.absolutePath.contains("x86_64") // for liblz4-java.dylib
                )
    } else if (currentOS == OS.WINDOWS) {
        return !(
                file.absolutePath.endsWith("win32-aarch64\\jnidispatch.dll") ||
                        file.absolutePath.endsWith("win32-x86\\jnidispatch.dll") ||
                        file.absolutePath.endsWith("aarch64\\sqlitejdbc.dll") ||
                        file.absolutePath.endsWith("aarch64\\sqlitejdbc.dll") ||
                        file.absolutePath.endsWith("armv7\\sqlitejdbc.dll") ||
                        file.absolutePath.endsWith("x86\\sqlitejdbc.dll")
                )
    } else {
        throw Exception("Unsupported OS: $currentOS")
    }
}

private fun codesignInJar(jarFile: File, nativeLibPath: File) {
    val tmpDir = createTempDirectory("codesignLibsInJarsTask").toFile()
    runCmd("unzip", "-q", jarFile.absolutePath, "-d", tmpDir.absolutePath)

    tmpDir.walk()
        .filter { it.isFile && isCodesignable(it) }
        .forEach { libFile ->
            codesign(libFile)

            if (isValidLibFile(libFile)) {
                runCmd("cp", libFile.absolutePath, nativeLibPath.absolutePath)
            }

            runCmd(
                "jar",
                "-uvf",
                jarFile.absolutePath,
                "-C", tmpDir.absolutePath,
                libFile.relativeToOrSelf(tmpDir).path
            )
        }

    tmpDir.deleteRecursively()
}

tasks.register("codesignLibsInJars") {
    dependsOn("copyDependencies", "copyJar")
    inputs.files(tasks.named("copyJar").get().outputs.files)

    val resourceNativePath = layout.buildDirectory.file("resources-native").get().asFile
    val nativeLibPath = layout.buildDirectory.file("resources-native").get().asFile.resolve("app/resources")

    doLast {
        resourceNativePath.deleteRecursively()
        nativeLibPath.mkdirs()
        inputs.files.forEach { file ->
            println("Process: ${file.absolutePath}")

            if (file.isDirectory) {
                file.walk()
                    .filter { it.isFile && it.extension == "jar" }
                    .forEach { codesignInJar(it, nativeLibPath) }
            } else if (file.extension == "jar") {
                codesignInJar(file, nativeLibPath)
            }
        }
    }
}


private fun codesignDir(dir: File) {
    dir.walk()
        .filter { it.isFile && isCodesignable(it) }
        .forEach { libFile ->
            codesign(libFile, EntitlementType.NoEntitlement)
        }
}

private fun removeQuarantine(file: File) {
    try {
        runCmd("/usr/bin/xattr", "-d", "com.apple.quarantine", file.absolutePath)
    } catch (_: IllegalStateException) {
    }
}

tasks.register("macosCodesignProvisionprofile") {
    onlyIf { currentOS == OS.MAC }

    doLast {
        removeQuarantine(provisionprofileDir.file("embedded.provisionprofile").asFile)
        codesign(provisionprofileDir.file("embedded.provisionprofile").asFile)

        removeQuarantine(provisionprofileDir.file("runtime.provisionprofile").asFile)
        codesign(provisionprofileDir.file("runtime.provisionprofile").asFile)
    }
}

tasks.register("jlink") {
    dependsOn("assemble", "codesignLibsInJars", "macosCodesignProvisionprofile")
    val jlinkBin = Paths.get(System.getProperty("java.home"), "bin", "jlink")

    inputs.files(tasks.named("copyJar").get().outputs.files)
    outputs.file(layout.buildDirectory.file("jlink"))
    outputs.files.singleFile.deleteRecursively()

    doLast {
        runCmd(
            jlinkBin.absolutePathString(),
            "--ignore-signing-information",
            "--strip-native-commands",
            "--no-header-files",
            "--no-man-pages",
            "--strip-debug",
            "-p",
            inputs.files.singleFile.absolutePath,
            "--module-path",
            "${System.getProperty("java.home")}/jmods;${inputs.files.singleFile.absolutePath}",
            "--add-modules",
            "java.base,java.desktop,java.logging,java.net.http,java.security.jgss,jdk.unsupported,java.security.sasl,jdk.crypto.ec,jdk.crypto.cryptoki,java.sql,java.management",
            "--output",
            outputs.files.singleFile.absolutePath,
        )
    }
}

tasks.register("prepareInfoPlist") {
    onlyIf { currentOS == OS.MAC }
    doLast {
        val template = layout.projectDirectory.file("mac-resources/Info.plist.template").asFile.readText()
        val content = template
            .replace("{{VERSION}}", version.toString())
            .replace("{{INTERNAL_VERSION}}", internalVersion)
            .replace("{{PACKAGE_IDENTIFIER}}", packageIdentifier)
            .replace("{{APP_NAME}}", appName)

        layout.projectDirectory.file("mac-resources/Info.plist").asFile.writeText(content)
    }
}

tasks.register("bareJpackage") {
    dependsOn("jlink", "prepareInfoPlist")
    val javaHome = System.getProperty("java.home")
    val jpackageBin = Paths.get(javaHome, "bin", "jpackage")

    val runtimeImage = tasks.named("jlink").get().outputs.files.singleFile
    val modulePath = tasks.named("copyJar").get().outputs.files.singleFile

    inputs.files(runtimeImage, modulePath)

    val outputDir = layout.buildDirectory.dir("bare-jpackage")
    val outputFile = if (currentOS == OS.MAC) {
        outputDir.get().asFile.resolve("${appName}-$version.dmg")
    } else if (currentOS == OS.WINDOWS) {
        outputDir.get().asFile.resolve("${appName}-$version.msi")
    } else {
        throw Exception("Unsupported OS: $currentOS")
    }

    outputs.file(outputFile)
    outputDir.get().asFile.deleteRecursively()

    doLast {
        // -XstartOnFirstThread is required for MacOS
        val maybeStartOnFirstThread = if (currentOS == OS.MAC) {
            "-XstartOnFirstThread"
        } else {
            ""
        }

        val javaOptionsArg = listOf(
            "--java-options",
            // -Djava.library.path=$APPDIR/resources is needed because we put the needed dylibs, jnilibs, and dlls there.
            "$maybeStartOnFirstThread -Dbackdoor.packaged=true -Djava.library.path=\$APPDIR/resources --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED"
        )

        val baseArgs = listOf(
            "--name", appName,
            "--app-version", version.toString(),
            "--main-jar", modulePath.resolve("${project.name}-$version.jar").absolutePath,
            "--main-class", mainClassName,
            "--runtime-image", runtimeImage.absolutePath,
            "--input", modulePath.absolutePath,
            "--dest", outputDir.get().asFile.absolutePath,
            "--vendor", "Tanin Na Nakorn",
            "--copyright", "2025 Tanin Na Nakorn",
            "--app-content", layout.buildDirectory.file("resources-native").get().asFile.resolve("app").absolutePath,
        ) + javaOptionsArg

        val platformSpecificArgs = if (currentOS == OS.MAC) {
            listOf(
                "--mac-package-identifier", packageIdentifier,
                "--mac-package-name", appName,
                "--mac-sign",
                "--mac-app-store",
                "--mac-signing-key-user-name", macDeveloperApplicationCertName,
                "--mac-entitlements", "entitlements.plist",
                "--resource-dir", layout.projectDirectory.dir("mac-resources").asFile.absolutePath,
                "--app-content", provisionprofileDir.file("embedded.provisionprofile").asFile.absolutePath,
            )
        } else if (currentOS == OS.WINDOWS) {
            listOf(
                "--icon", layout.projectDirectory.dir("win-resources").file("Backdoor.ico").asFile.absolutePath,
                "--type", "msi",
                "--win-menu",
                "--win-shortcut"
            )
        } else {
            throw Exception("Unsupported OS: $currentOS")
        }

        runCmd(*((listOf(jpackageBin.absolutePathString()) + baseArgs + platformSpecificArgs).toTypedArray()))
    }
}

tasks.register("jpackageForMac") {
    onlyIf { currentOS == OS.MAC }
    dependsOn("bareJpackage")

    inputs.file(tasks.named("bareJpackage").get().outputs.files.singleFile)

    val outputAppDir = layout.buildDirectory.dir("extracted-dmg").get().asFile
    val outputAppFile = outputAppDir.resolve("$appName.app")
    val outputDmgDir = layout.buildDirectory.dir("dmg").get().asFile
    val outputDmgFile = outputDmgDir.resolve(inputs.files.singleFile.name)

    outputs.dir(outputAppFile)
    outputs.file(outputDmgFile)

    doLast {
        outputAppDir.deleteRecursively()
        outputAppDir.mkdirs()
        var volumeName: String? = null
        val output = runCmd("/usr/bin/hdiutil", "attach", "-readonly", inputs.files.singleFile.absolutePath)

        volumeName = output.lines()
            .firstNotNullOfOrNull { line -> Regex("/Volumes/([^ ]*)").find(line)?.groupValues?.get(1) }

        println("Found /Volumes/$volumeName/")

        if (volumeName == null) {
            throw Exception("Unable to extract the volumn name from the hdiutil command. Output: $output")
        }

        runCmd("cp", "-R", "/Volumes/$volumeName/.", outputAppFile.parentFile.absolutePath)
        runCmd("/usr/bin/hdiutil", "detach", "/Volumes/$volumeName")
        runCmd("/usr/bin/open", outputAppFile.parentFile.absolutePath)

        // Prepare runtime
        Files.copy(
            provisionprofileDir.file("runtime.provisionprofile").asFile.toPath(),
            outputAppFile.resolve("Contents/runtime/Contents/embedded.provisionprofile").toPath()
        )

        codesignDir(outputAppFile.resolve("Contents/runtime"))

        codesign(
            outputAppFile.resolve("Contents/runtime/Contents/Home/lib/jspawnhelper"),
            EntitlementType.RuntimeEntitlement
        )
        codesign(outputAppFile.resolve("Contents/runtime"))
        codesign(outputAppFile, EntitlementType.MainEntitlement)

        outputDmgDir.deleteRecursively()
        outputDmgDir.mkdirs()

        // Sometimes we need to unlock the *.app file in order to allow hdiutil to package it into DMG.
        // It seems to happen when TestFlight is reusing the file.
        runCmd("chflags", "nouchg,noschg", outputAppFile.absolutePath)

        runCmd(
            "/usr/bin/hdiutil",
            "create",
            "-ov",
            "-srcFolder", outputAppDir.absolutePath,
            outputDmgFile.absolutePath,
        )
    }
}

tasks.register("jpackageForWindows") {
    onlyIf { currentOS == OS.WINDOWS }
    dependsOn("bareJpackage")

    inputs.file(tasks.named("bareJpackage").get().outputs.files.singleFile)

    val outputAppDir = layout.buildDirectory.dir("msi").get().asFile

    outputs.dir(outputAppDir)

    doLast {
        outputAppDir.deleteRecursively()
        outputAppDir.mkdirs()

        runCmd(
            File(System.getenv("CODESIGN_TOOL_DIR")).canonicalFile,
            "cmd",
            "/c",
            listOf(
                "CodeSignTool.bat",
                "sign",
                "-input_file_path=${inputs.files.singleFile.absolutePath}",
                "-output_dir_path=${outputAppDir.absolutePath}",
                "-program_name=${appName}",
                "-username=${System.getenv("SSL_COM_USERNAME")}",
                "-password=${System.getenv("SSL_COM_PASSWORD")}",
                "-totp_secret=${System.getenv("SSL_COM_TOTP_SECRET")}",
            )
                .joinToString(" ")
                .replace("\\", "/")
        )
    }
}

tasks.register("jpackage") {
    dependsOn("bareJpackage", "jpackageForMac", "jpackageForWindows")
}

tasks.register<Exec>("notarize") {
    dependsOn("jpackageForMac")

    inputs.file(tasks.named("jpackageForMac").get().outputs.files.filter { it.extension == "dmg" }.first())

    commandLine(
        "/usr/bin/xcrun",
        "notarytool",
        "submit",
        "--wait",
        "--apple-id", appleEmail!!,
        "--password", appleAppSpecificPassword!!,
        "--team-id", teamId,
        inputs.files.singleFile.absolutePath,
    )
}


tasks.register<Exec>("staple") {
    dependsOn("notarize")

    inputs.file(tasks.named("jpackageForMac").get().outputs.files.filter { it.extension == "dmg" }.first())

    commandLine(
        "/usr/bin/xcrun",
        "stapler",
        "staple",
        "-v",
        inputs.files.singleFile.absolutePath,
    )
}



tasks.register<Exec>("convertToPkg") {
    dependsOn("jpackageForMac")
    val app = tasks.named("jpackageForMac").get().outputs.files.filter { it.extension == "app" }.first()
    inputs.dir(app)
    outputs.file(layout.buildDirectory.dir("pkg").get().file("$appName.pkg"))
    commandLine(
        "/usr/bin/productbuild",
        "--sign", "3rd Party Mac Developer Installer: Tanin Na Nakorn (S6482XAL5E)",
        "--component", app.absolutePath,
        "/Applications",
        outputs.files.singleFile.absolutePath,
    )
}


tasks.register("validatePkg") {
    dependsOn("convertToPkg")
    inputs.file(tasks.named("convertToPkg").get().outputs.files.singleFile)

    doLast {
        runCmd(
            layout.projectDirectory.asFile,
            "/usr/bin/xcrun",
            "altool",
            "--validate-app",
            "-f", inputs.files.singleFile.absolutePath,
            "-t", "osx",
            "-u", appleEmail!!,
            "-p", appleAppSpecificPassword!!
        )
    }
}


tasks.register("uploadPkgToAppStore") {
    dependsOn("validatePkg")
    inputs.file(tasks.named("convertToPkg").get().outputs.files.singleFile)
    doLast {
        runCmd(
            layout.projectDirectory.asFile,
            "/usr/bin/xcrun",
            "altool",
            "--upload-app",
            "-f", inputs.files.singleFile.absolutePath,
            "-t", "osx",
            "-u", appleEmail!!,
            "-p", appleAppSpecificPassword!!
        )
    }
}
