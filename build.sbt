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
  "dev.zio" %% "zio"               % versions.zio,
  "dev.zio" %% "zio-macros"        % versions.zio,
  "dev.zio" %% "zio-logging"       % versions.`zio-logging`,
  "dev.zio" %% "zio-logging-slf4j" % versions.`zio-logging`,
  "dev.zio" %% "zio-http"          % versions.`zio-http`
)

lazy val logging = Seq(
  "ch.qos.logback"             % "logback-classic"          % "1.4.5",
  "ch.qos.logback"             % "logback-core"             % "1.4.5",
  "net.logstash.logback"       % "logstash-logback-encoder" % "7.2",
  "com.fasterxml.jackson.core" % "jackson-databind"         % "2.14.0"
)

lazy val tests = Seq(
  "dev.zio" %% "zio-test"     % versions.zio % "test,it",
  "dev.zio" %% "zio-test-sbt" % versions.zio % "test,it"
)

lazy val `tapad-analytics` = project
  .in(file("."))
  .settings(
    version := versions.app,
    scalaVersion := versions.scala,
    scalacOptions ++= scalacCustomOptions,
    libraryDependencies ++= zio ++ logging ++ tests,
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
