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

val sentryDsn = getSecret("SENTRY_DSN", "../secret/SENTRY_DSN")
val sentryWebviewDsn = getSecret("SENTRY_WEBVIEW_DSN", "../secret/SENTRY_WEBVIEW_DSN")

group = "tanin.backdoor"
version = "2.8.0-rc1"

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
    implementation(project(":core"))
    implementation("io.github.tanin47:jmigrate:0.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.36.0")
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
                "release=backdoor-web@${project.version}"
            ).joinToString("\n")
        )
    }
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

tasks.compileJava {
    dependsOn("writeVersionAndSentryProperties")
}

application {
    mainClass.set("tanin.backdoor.web.Main")
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

tasks.shadowJar {
    archiveClassifier.set("") // Remove the suffix -all.
    relocate("com.", "tanin.backdoor.com.")
    relocate("org.", "tanin.backdoor.org.")
    relocate("net.", "tanin.backdoor.net.")
    exclude("META-INF/MANIFEST.MF")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "tanin.backdoor.web.Main"
}

// For CI validation.
tasks.register("printVersion") {
    doLast {
        print("$version")
    }
}
