import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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

group = "tanin.backdoor.core"
description = "Backdoor: Database Querying and Editing Tool"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
    sourceSets {
        main {
            resources {
                srcDir("build/compiled-frontend-resources")
            }
        }
    }
}

repositories {
    mavenCentral()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report

    reports {
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}


dependencies {
    api("com.renomad:minum:8.3.1")
    api("org.postgresql:postgresql:42.7.8")
    api("com.clickhouse:jdbc-v2:0.9.3")
    api("org.xerial:sqlite-jdbc:3.51.0.0")
    api("com.eclipsesource.minimal-json:minimal-json:0.9.5")
    api("org.altcha:altcha:1.2.0")
    api("io.sentry:sentry-jul:8.28.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.36.0")
}

tasks.named<Test>("test") {
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

val executableExt = if (currentOS == OS.WINDOWS) ".cmd" else ""

tasks.register<Exec>("compileTailwind") {
    workingDir = layout.projectDirectory.dir("..").asFile

    environment("NODE_ENV", "production")

    commandLine(
        "./node_modules/.bin/postcss$executableExt",
        "./frontend/stylesheets/tailwindbase.css",
        "--config",
        ".",
        "--output",
        "./core/build/compiled-frontend-resources/assets/stylesheets/tailwindbase.css"
    )
}

tasks.register<Exec>("compileSvelte") {
    workingDir = layout.projectDirectory.dir("..").asFile

    environment("NODE_ENV", "production")
    environment("ENABLE_SVELTE_CHECK", "true")

    commandLine(
        "./node_modules/.bin/webpack${executableExt}",
        "--config",
        "./webpack.config.js",
        "--output-path",
        "./core/build/compiled-frontend-resources/assets",
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
