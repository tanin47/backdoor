import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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
val appleEmail = if (System.getenv("APPLE_EMAIL") != null) {
    System.getenv("APPLE_EMAIL")
} else {
    try {
        project.file("../secret/APPLE_EMAIL").readText().trim()
    } catch (e: Exception) {
        "The-apple-email-is-not-specified"
    }
}
val appleAppSpecificPassword = if (System.getenv("APPLE_APP_SPECIFIC_PASSWORD") != null) {
    System.getenv("APPLE_APP_SPECIFIC_PASSWORD")
} else {
    try {
        project.file("../secret/APPLE_APP_SPECIFIC_PASSWORD").readText().trim()
    } catch (e: Exception) {
        "The-apple-app-specific-password-is-not-specified"
    }
}

val teamId = "S6482XAL5E"
val appName = "Backdoor"
val packageIdentifier = "tanin.backdoor.desktop.macos"
group = "tanin.backdoor.desktop"
version = "1.0"
val internalVersion = "1.0.14"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    sourceSets {
        main {
            resources {
                srcDir("build/compiled-frontend-resources")
            }
        }
    }
}

tasks.register("compileSwift") {
    group = "build"
    description = "Compile Swift code and output the dylib to the resource directory."

    val inputFile = layout.projectDirectory.file("src/main/swift/MacOsApi.swift").asFile
    val outputFile = layout.projectDirectory.file("src/main/resources/native/libMacOsApi.dylib").asFile

    println("Compiling Swift code to $outputFile")

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

tasks.register("writeVersion") {
    doLast {
        file("src/main/resources/version.properties").writeText("version=${project.version}")
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("compileSwift", "writeVersion")
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
    // TODO: Remove after we support tests.
    enabled = false
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
    applicationDefaultJvmArgs = listOf(
        "-XstartOnFirstThread",
        "--add-exports",
        "java.base/sun.security.x509=ALL-UNNAMED",
        "--add-exports",
        "java.base/sun.security.tools.keytool=ALL-UNNAMED",
    )
}

tasks.jar {
    manifest.attributes["Main-Class"] = mainClassName
}

tasks.register<Exec>("compileTailwind") {
    workingDir = layout.projectDirectory.dir("..").asFile

    environment("NODE_ENV", "production")

    commandLine(
        "./node_modules/.bin/postcss",
        "./frontend/stylesheets/tailwindbase.css",
        "--config",
        ".",
        "--output",
        "./desktop/build/compiled-frontend-resources/assets/stylesheets/tailwindbase.css"
    )
}

tasks.register<Exec>("compileSvelte") {
    workingDir = layout.projectDirectory.dir("..").asFile

    environment("NODE_ENV", "production")
    environment("ENABLE_SVELTE_CHECK", "true")

    commandLine(
        "./node_modules/webpack/bin/webpack.js",
        "--config",
        "./webpack.config.js",
        "--output-path",
        "./desktop/build/compiled-frontend-resources/assets",
        "--mode",
        "production"
    )
}

tasks.processResources {
    dependsOn("compileTailwind")
    dependsOn("compileSvelte")
}

tasks.named("sourcesJar") {
    dependsOn("compileTailwind")
    dependsOn("compileSvelte")
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

private fun runCmd(vararg args: String): String {
    println("Executing command: ${args.joinToString(" ")}")

    val output = StringBuilder()
    val process = ProcessBuilder(*args)
        .directory(layout.projectDirectory.asFile)
        .start()

    // Print stdout
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

private fun codesign(file: File, useRuntimeEntitlement: Boolean = false) {
    runCmd(
        "codesign",
        "-vvvv",
        "--options",
        "runtime",
        "--entitlements",
        if (useRuntimeEntitlement) {
            "runtime-entitlements.plist"
        } else {
            "entitlements.plist"
        },
        "--timestamp",
        "--force",
        "--sign",
        macDeveloperApplicationCertName,
        file.absolutePath,
    )
}

private fun isCodesignable(file: File): Boolean {
    val excludedExtensions = setOf("so", "a", "xml")
    return file.extension == "dylib" ||
            file.extension == "jnilib" ||
            (!excludedExtensions.contains(file.extension) && file.toPath().isExecutable())
}

private fun codesignInJar(jarFile: File, nativeLibPath: File) {
    val tmpDir = createTempDirectory("MacosCodesignLibsInJarsTask").toFile()
    runCmd("unzip", "-q", jarFile.absolutePath, "-d", tmpDir.absolutePath)

    tmpDir.walk()
        .filter { it.isFile && isCodesignable(it) }
        .forEach { libFile ->
            println("")
            codesign(libFile)

            if (libFile.absolutePath.contains("darwin-x86-64")) {
                // Skip the jna's x86-64 lib
            } else {
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

tasks.register("macosCodesignLibsInJars") {
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


private fun codesignDir(dir: File, useRuntimeEntitlement: Boolean = false) {
    dir.walk()
        .filter { it.isFile && isCodesignable(it) }
        .forEach { libFile ->
            codesign(libFile, useRuntimeEntitlement)
        }
}

private fun removeQuarantine(file: File) {
    try {
        runCmd("/usr/bin/xattr", "-d", "com.apple.quarantine", file.absolutePath)
    } catch (_: IllegalStateException) {
    }
}

tasks.register("macosCodesignProvisionprofile") {
    doLast {
        removeQuarantine(provisionprofileDir.file("embedded.provisionprofile").asFile)
        codesign(provisionprofileDir.file("embedded.provisionprofile").asFile)

        removeQuarantine(provisionprofileDir.file("runtime.provisionprofile").asFile)
        codesign(provisionprofileDir.file("runtime.provisionprofile").asFile, useRuntimeEntitlement = true)
    }
}

tasks.register("jlink") {
    dependsOn("assemble", "macosCodesignLibsInJars", "macosCodesignProvisionprofile")
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
    val outputFile = outputDir.get().asFile.resolve("${appName}-$version.dmg")

    outputs.file(outputFile)
    outputDir.get().asFile.deleteRecursively()

    doLast {
        runCmd(
            jpackageBin.absolutePathString(),
            "--name", appName,
            "--app-version", version.toString(),
            "--main-jar", modulePath.resolve("${project.name}-$version.jar").absolutePath,
            "--main-class", mainClassName,
            "--runtime-image", runtimeImage.absolutePath,
            "--input", modulePath.absolutePath,
            "--dest", outputDir.get().asFile.absolutePath,
            "--mac-package-identifier", packageIdentifier,
            "--mac-package-name", appName,
            "--mac-sign",
            "--mac-app-store",
            "--mac-signing-key-user-name", macDeveloperApplicationCertName,
            "--mac-entitlements", "entitlements.plist",
            "--resource-dir", layout.projectDirectory.dir("mac-resources").asFile.absolutePath,
            "--app-content", provisionprofileDir.file("embedded.provisionprofile").asFile.absolutePath,
            "--app-content", layout.buildDirectory.file("resources-native").get().asFile.resolve("app").absolutePath,
            // -XstartOnFirstThread requires for MacOs
            "--java-options",
            "-XstartOnFirstThread --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED"
        )
    }
}

tasks.register("jpackage") {
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
        outputDmgDir.deleteRecursively()
        outputDmgDir.mkdirs()
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

        codesignDir(outputAppFile.resolve("Contents/runtime"), useRuntimeEntitlement = true)

        codesign(outputAppFile.resolve("Contents/runtime"), useRuntimeEntitlement = true)
        codesign(outputAppFile)

        runCmd(
            "/usr/bin/hdiutil",
            "create",
            "-ov",
            "-srcFolder", outputAppDir.absolutePath,
            outputDmgFile.absolutePath,
        )
    }
}

tasks.register<Exec>("notarize") {
    dependsOn("jpackage")

    inputs.file(tasks.named("jpackage").get().outputs.files.filter { it.extension == "dmg" }.first())

    commandLine(
        "/usr/bin/xcrun",
        "notarytool",
        "submit",
        "--wait",
        "--apple-id", appleEmail,
        "--password", appleAppSpecificPassword,
        "--team-id", teamId,
        inputs.files.singleFile.absolutePath,
    )
}


tasks.register<Exec>("staple") {
    dependsOn("notarize")

    inputs.file(tasks.named("jpackage").get().outputs.files.filter { it.extension == "dmg" }.first())

    commandLine(
        "/usr/bin/xcrun",
        "stapler",
        "staple",
        "-v",
        inputs.files.singleFile.absolutePath,
    )
}



tasks.register<Exec>("convertToPkg") {
    dependsOn("jpackage")
    val app = tasks.named("jpackage").get().outputs.files.filter { it.extension == "app" }.first()
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


tasks.register<Exec>("validatePkg") {
    dependsOn("convertToPkg")
    inputs.file(tasks.named("convertToPkg").get().outputs.files.singleFile)
    commandLine(
        "/usr/bin/xcrun",
        "altool",
        "--validate-app",
        "-f", inputs.files.singleFile.absolutePath,
        "-t", "osx",
        "-u", appleEmail,
        "-p", appleAppSpecificPassword
    )
}


tasks.register<Exec>("uploadPkgToAppStore") {
    dependsOn("validatePkg")
    inputs.file(tasks.named("convertToPkg").get().outputs.files.singleFile)
    commandLine(
        "/usr/bin/xcrun",
        "altool",
        "--upload-app",
        "-f", inputs.files.singleFile.absolutePath,
        "-t", "osx",
        "-u", appleEmail,
        "-p", appleAppSpecificPassword
    )
}
