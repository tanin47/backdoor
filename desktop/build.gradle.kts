import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory

plugins {
    `java-library`
    application
    jacoco
}

// TODO: Replace the below with the name of your 'Developer ID Application' cert which you can get from https://developer.apple.com/account/resources/certificates/list
val macDeveloperApplicationCertName = "Developer ID Application: Tanin Na Nakorn (S6482XAL5E)"
// TODO: Replace the below with the prefix of your bundle ID which you can get from https://developer.apple.com/account/resources/identifiers/list
val codesignPackagePrefix = "tanin.backdoor.desktop."

group = "tanin.backdoor.desktop"
version = "1.0.0"

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

tasks.register<Exec>("compileSwift") {
    group = "build"
    description = "Compile Swift code and output the dylib to the resource directory."

    val inputFile = layout.projectDirectory.file("src/main/swift/MacOsApi.swift").asFile
    val outputFile = layout.projectDirectory.file("src/main/resources/libMacOsApi.dylib").asFile

    println("Compiling Swift code to $outputFile")

    commandLine(
        "swiftc",
        "-emit-library",
        inputFile.absolutePath,
        "-target",
        "arm64-apple-macos11",
        "-o",
        outputFile.absolutePath,
    )
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("compileSwift")
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
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.casterlabs.co/maven") }
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
    inputs.files(fileTree("frontend"))
    outputs.dir("./build/compiled-frontend-resources")

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
    inputs.files(fileTree("frontend"))
    outputs.dir("./build/compiled-frontend-resources")

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
tasks.register("printVersion") {
    doLast {
        print("$version")
    }
}

tasks.register("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath).into(layout.buildDirectory.dir("jmods"))
}

tasks.register("copyJar", Copy::class) {
    from(tasks.jar).into(layout.buildDirectory.dir("jmods"))
}

private fun runCmd(vararg args: String) {
    println("Executing command: ${args.joinToString(" ")}")

    val retVal = ProcessBuilder(*args)
        .directory(layout.projectDirectory.asFile)
        .start()
        .waitFor()

    if (retVal != 0) {
        throw IllegalStateException("Command execution failed with return value: $retVal")
    }
}

private fun signInJar(jarFile: File) {
    val tmpDir = createTempDirectory("MacosCodesignLibsInJarsTask").toFile()
    runCmd("unzip", "-q", jarFile.absolutePath, "-d", tmpDir.absolutePath)

    tmpDir.walk()
        .filter { it.isFile && (it.extension == "dylib" || it.extension == "jnilib") }
        .forEach { libFile ->
            println("")
            runCmd(
                "codesign",
                "-vvvv",
                "--options",
                "runtime",
                "--entitlements",
                "entitlements.plist",
                "--timestamp",
                "--force",
                "--prefix",
                codesignPackagePrefix,
                "--sign",
                macDeveloperApplicationCertName,
                libFile.absolutePath
            )

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

    doLast {
        inputs.files.forEach { file ->
            println("Process: ${file.absolutePath}")

            if (file.isDirectory) {
                file.walk()
                    .filter { it.isFile && it.extension == "jar" }
                    .forEach { signInJar(it) }
            } else if (file.extension == "jar") {
                signInJar(file)
            }
        }
    }
}

tasks.register<Exec>("jdeps") {
    dependsOn("assemble")
    val jdepsBin = Paths.get(System.getProperty("java.home"), "bin", "jdeps")
    commandLine(
        jdepsBin,
        "--module-path", tasks.named<JavaCompile>("compileJava").get().classpath.asPath,
        "--multi-release", "base",
        "--print-module-deps",
        tasks.jar.get().outputs.files.asPath,
    )
}

tasks.register<Exec>("jlink") {
    dependsOn("assemble", "macosCodesignLibsInJars")
    val jlinkBin = Paths.get(System.getProperty("java.home"), "bin", "jlink")

    inputs.files(tasks.named("copyJar").get().outputs.files)
    outputs.file(layout.buildDirectory.file("jlink"))
    outputs.files.singleFile.deleteRecursively()

    commandLine(
        jlinkBin,
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

tasks.register<Exec>("jpackage") {
    dependsOn("jlink")
    val javaHome = System.getProperty("java.home")
    val jpackageBin = Paths.get(javaHome, "bin", "jpackage")

    val runtimeImage = tasks.named("jlink").get().outputs.files.singleFile
    val modulePath = tasks.named("copyJar").get().outputs.files.singleFile

    inputs.files(runtimeImage, modulePath)

    val outputDir = layout.buildDirectory.dir("jpackage")
    val outputFile = outputDir.get().asFile.resolve("${project.name}-$version.dmg")

    outputs.file(outputFile)
    outputDir.get().asFile.deleteRecursively()

    commandLine(
        jpackageBin,
        "--name",
        project.name,
        "--app-version",
        version,
        "--main-jar",
        modulePath.resolve("${project.name}-$version.jar"),
        "--main-class",
        mainClassName,
        "--runtime-image",
        runtimeImage.absolutePath,
        "--vendor",
        "tanin.javaelectron",
        "--input",
        modulePath.absolutePath,
        "--dest",
        outputDir.get().asFile.absolutePath,
        "--mac-package-identifier",
        "tanin.javaelectron.macos.app",
        "--mac-package-name",
        "Java Electron",
        "--mac-package-signing-prefix",
        codesignPackagePrefix,
        "--mac-sign",
        "--mac-signing-key-user-name",
        macDeveloperApplicationCertName,
        "--mac-entitlements",
        "entitlements.plist",
        // -XstartOnFirstThread requires for MacOs
        "--java-options",
        "-XstartOnFirstThread --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED"
    )
}


interface InjectedExecOps {
    @get:Inject
    val execOps: ExecOperations
}

tasks.register("jpackageAndExtractApp") {
    dependsOn("jpackage")

    inputs.file(tasks.named("jpackage").get().outputs.files.singleFile)
    outputs.dir(layout.buildDirectory.dir("jpackage-extracted-app"))
    outputs.files.singleFile.deleteRecursively()

    val injected = project.objects.newInstance<InjectedExecOps>()
    doLast {
        var volumeName: String? = null
        val output = ByteArrayOutputStream()
        injected.execOps.exec {
            commandLine("/usr/bin/hdiutil", "attach", "-readonly", inputs.files.singleFile.absolutePath)
            standardOutput = output
        }

        volumeName = output.toString().lines()
            .firstNotNullOfOrNull { line -> Regex("/Volumes/([^ ]*)").find(line)?.groupValues?.get(1) }

        if (volumeName == null) {
            throw Exception("Unable to extract the volumn name from the hdiutil command. Output: $output")
        }
        injected.execOps.exec {
            commandLine("cp", "-R", "/Volumes/$volumeName/.", outputs.files.singleFile.absolutePath)
        }

        injected.execOps.exec {
            commandLine("/usr/bin/hdiutil", "detach", "/Volumes/$volumeName")
        }

        injected.execOps.exec {
            commandLine("/usr/bin/open", outputs.files.singleFile.absolutePath)
        }
    }
}

tasks.register<Exec>("notarize") {
    dependsOn("jpackage")

    inputs.file(tasks.named("jpackage").get().outputs.files.singleFile)

    commandLine(
        "/usr/bin/xcrun",
        "notarytool",
        "submit",
        "--wait",
        // TODO: Replace -p value with your notarytool profile's name. You can set it up using `xcrun notarytool store-credentials`.
        "-p", "personal",
        inputs.files.singleFile.absolutePath,
    )
}


tasks.register<Exec>("staple") {
    dependsOn("notarize")

    inputs.file(tasks.named("jpackage").get().outputs.files.singleFile)

    commandLine(
        "/usr/bin/xcrun",
        "stapler",
        "staple",
        "-v",
        inputs.files.singleFile.absolutePath,
    )
}
