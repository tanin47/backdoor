import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jreleaser.model.Active
import org.jreleaser.model.Signing.Mode

plugins {
    `java-library`
    application
    `maven-publish`
    jacoco
    id("org.jreleaser") version "1.21.0"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "tanin.backdoor"
version = "2.3.0-rc1"

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
    implementation("com.renomad:minum:8.2.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("com.clickhouse:jdbc-v2:0.9.3")
    implementation("com.eclipsesource.minimal-json:minimal-json:0.9.5")
    implementation("org.altcha:altcha:1.2.0")

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

application {
    mainClass.set("tanin.backdoor.BackdoorServer")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.tanin47"
            artifactId = "backdoor"
            version = project.version.toString()
            artifact(tasks.shadowJar)
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set("Backdoor")
                description.set("Database querying and editing tool for you and your team")
                url.set("https://github.com/tanin47/backdoor")
                inceptionYear.set("2025")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://spdx.org/licenses/MIT.html")
                    }
                }
                developers {
                    developer {
                        id.set("tanin47")
                        name.set("Tanin Na Nakorn")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/tanin47/backdoor.git")
                    developerConnection.set("scm:git:ssh://github.com/tanin47/backdoor.git")
                    url.set("http://github.com/tanin47/backdoor")
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    signing {
        active = Active.ALWAYS
        armored = true
        mode = if (System.getenv("CI") != null) Mode.MEMORY else Mode.COMMAND
        command {
            executable = "/opt/homebrew/bin/gpg"
        }
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    setActive("ALWAYS")
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}

tasks.register<Exec>("compileTailwind") {
    inputs.files(fileTree("frontend"))
    outputs.dir("build/compiled-frontend-resources")

    environment("NODE_ENV", "production")

    commandLine(
        "./node_modules/.bin/postcss",
        "./frontend/stylesheets/tailwindbase.css",
        "--config",
        ".",
        "--output",
        "./build/compiled-frontend-resources/assets/stylesheets/tailwindbase.css"
    )
}

tasks.register<Exec>("compileSvelte") {
    inputs.files(fileTree("frontend"))
    outputs.dir("build/compiled-frontend-resources")

    environment("NODE_ENV", "production")
    environment("ENABLE_SVELTE_CHECK", "true")

    commandLine(
        "./node_modules/webpack/bin/webpack.js",
        "--config",
        "./webpack.config.js",
        "--output-path",
        "./build/compiled-frontend-resources/assets",
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

tasks.shadowJar {
    archiveClassifier.set("") // Remove the suffix -all.
    relocate("com.", "tanin.backdoor.com.")
    relocate("org.", "tanin.backdoor.org.")
    relocate("net.", "tanin.backdoor.net.")
    exclude("META-INF/MANIFEST.MF")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "tanin.backdoor.BackdoorServer"
}

// For CI validation.
tasks.register("printVersion") {
    doLast {
        print("$version")
    }
}
