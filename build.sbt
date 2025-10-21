import sbt.{ThisBuild, url}

lazy val root = project in file(".")

name := "backdoor-dev"
ThisBuild / version := "1.1.0"

ThisBuild / organization := "tanin.backdoor"

Global / onChangedBuildSource := ReloadOnSourceChanges

autoScalaLibrary := false
libraryDependencies ++= Seq(
  "com.renomad" % "minum" % "8.2.0",
  "org.postgresql" % "postgresql" % "42.7.8",
  "com.eclipsesource.minimal-json" % "minimal-json" % "0.9.5",
  "org.junit.jupiter" % "junit-jupiter" % "6.0.0" % Test,
  "org.seleniumhq.selenium" % "selenium-java" % "4.36.0" % Test,
  "com.github.sbt.junit" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test
)

Compile / packageDoc / publishArtifact := false
Compile / doc / sources := Seq.empty

testOptions += Tests.Argument(jupiterTestFramework, "-q", "-v")
Test / parallelExecution := false

Compile / run / mainClass := Some("tanin.backdoor.BackdoorServer")
assembly / mainClass := Some("tanin.backdoor.BackdoorServer")

publish / skip := true

assembly / assemblyJarName := "backdoor.jar"

ThisBuild / assemblyMergeStrategy := {
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "local_dev_marker.ejwf" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

ThisBuild / assemblyShadeRules := Seq(
  ShadeRule.rename("com.**" -> "tanin.backdoor.com.@1").inAll
)

lazy val fatJar = project
  .settings(
    name := "backdoor",
    autoScalaLibrary := false,
    crossPaths := false,
    Compile / packageBin := (root / assembly).value,
    ThisBuild / organization := "io.github.tanin47",
    ThisBuild / organizationName := "tanin47",
    ThisBuild / organizationHomepage := Some(url("https://github.com/tanin47")),
    ThisBuild / homepage := Some(url("https://github.com/tanin47/backdoor")),
    ThisBuild / scmInfo := Some(
      ScmInfo(
        url("https://github.com/tanin47/backdoor"),
        "scm:git@github.com:tanin47/backdoor.git"
      )
    ),
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_central_credentials"),
    Test / publishArtifact := false,
    ThisBuild / publishMavenStyle := true,
    ThisBuild / pomIncludeRepository := { _ => false },
    ThisBuild / publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
      else localStaging.value
    },
    ThisBuild / licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
    ThisBuild / developers := List(
      Developer(
        id = "tanin",
        name = "Tanin Na Nakorn",
        email = "@tanin",
        url = url("https://github.com/tanin47")
      )
    ),
    versionScheme := Some("semver-spec"),
  )
