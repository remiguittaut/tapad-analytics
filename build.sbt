import sbt._
import sbt.Keys._

val app = "tapad-analytics"

version := versions.app
scalaVersion := versions.scala
name := app

val scalacCustomOptions = Seq(
  "-encoding",
  "UTF-8",
  "-opt-warnings",
  "-feature",
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-extra-implicit",
  "-Ywarn-numeric-widen",
  "-Ymacro-annotations",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Ywarn-value-discard",
  "-Wunused",
  "-Wconf:cat=lint-package-object-classes:s,cat=lint-byname-implicit:s"
)

lazy val zio = Seq(
  "dev.zio" %% "zio"                 % versions.zio,
  "dev.zio" %% "zio-macros"          % versions.zio,
  "dev.zio" %% "zio-logging"         % versions.`zio-logging`,
  "dev.zio" %% "zio-logging-slf4j"   % versions.`zio-logging`,
  "dev.zio" %% "zio-http"            % versions.`zio-http`,
  "dev.zio" %% "zio-config"          % versions.`zio-config`,
  "dev.zio" %% "zio-config-magnolia" % versions.`zio-config`,
  "dev.zio" %% "zio-config-typesafe" % versions.`zio-config`
)

lazy val sttp = Seq(
  "com.softwaremill.sttp.client3" %% "core" % versions.sttp,
  "com.softwaremill.sttp.client3" %% "zio"  % versions.sttp
)

lazy val logging = Seq(
  "org.slf4j" % "slf4j-simple" % "2.0.5"
)

lazy val tests = Seq(
  "dev.zio" %% "zio-test"          % versions.zio % Test,
  "dev.zio" %% "zio-test-sbt"      % versions.zio % Test,
  "dev.zio" %% "zio-test-magnolia" % versions.zio % Test
)

lazy val `tapad-analytics` = project
  .in(file("."))
  .settings(
    version := versions.app,
    scalaVersion := versions.scala,
    scalacOptions ++= scalacCustomOptions,
    libraryDependencies ++= zio ++ logging ++ tests ++ sttp,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

inThisBuild(
  List(
    semanticdbEnabled := true, // enable SemanticDB
    semanticdbOptions += "-P:semanticdb:synthetics:on",
    semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
    scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(versions.scala),
    scalafixDependencies ++= List(
      "com.github.liancheng" %% "organize-imports" % "0.6.0",
      "com.github.vovapolu"  %% "scaluzzi"         % "0.1.23"
    )
  )
)

Global / onChangedBuildSource := ReloadOnSourceChanges

Compile / mainClass := Some("com.tapad.analytics.Startup")
Compile / unmanagedClasspath ++= Seq(
  sourceDirectory.value / "main" / "resources"
)

run / fork := true
run / javaOptions ++= Seq("-Duser.timezone=UTC", "-Dkryo.unsafe=false")
Test / fork := true

// Aliases

addCommandAlias("prepare", "fix; fmt")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
addCommandAlias("fix", "scalafixAll")
addCommandAlias("fixCheck", "scalafixAll --check")
addCommandAlias("ci-lint", "fixCheck; fmtCheck")
