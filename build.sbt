import sbt.Keys._

organization := "org.chepelov.sgcib.kata"
name := "kata-merry-xmas"
version := "0.0.1-SNAPSHOT"
description := "A service enabling basic banking account operations"

scalaVersion := "2.12.10"

scalacOptions ++= Seq(
  "-Xmacro-settings:materialize-derivations",
  // Enables compilation errors on non-exhaustive pattern matchings.
  // Use @silent annotations to consciously silent out warnings if needed
  // (https://github.com/ghik/silencer).
  "-Xfatal-warnings",
)

val silencerVersion = "1.4.4"
val scalatestVersion = "3.1.0"
val zioVersion = "1.0.0-RC17"


libraryDependencies ++= Seq(
  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
)

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,

  "com.propensive" %% "kaleidoscope" % "0.1.0",
)

libraryDependencies ++= Seq(
    "org.scalactic" %% "scalactic" % scalatestVersion,
    "org.scalatest" %% "scalatest" % scalatestVersion % Test,
    "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test,
    "org.scalacheck" %% "scalacheck" % "1.14.2" % Test,
    "org.scalamock" %% "scalamock" % "4.4.0" % Test,
    "com.lihaoyi" %% "pprint" % "0.5.6" % Test
)



