import sbt._
import Keys._

object BuildSettings {
  val paradiseVersion = "2.0.0"
  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "com.scalawit",
    version := "0.1",
    scalacOptions ++= Seq(),
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.2", "2.10.3", "2.10.4"),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
  )
}

object MyBuild extends Build {
  import BuildSettings._

  lazy val root: Project = Project(
    "root",
    file("."),
    settings = buildSettings ++ Seq(
      run <<= run in Compile in core
    )
  ) aggregate(core)

  lazy val macros: Project = Project(
    "macros",
    file("macros"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
      libraryDependencies ++= (
        if (scalaVersion.value.startsWith("2.10")) List("org.scalamacros" %% "quasiquotes" % paradiseVersion)
        else Nil
        )
    )
  )

  lazy val core: Project = Project(
    "core",
    file("core"),
    settings = buildSettings ++ sbtassembly.Plugin.assemblySettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.1.4" % "test",
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
        "com.typesafe.play" %% "play-json" % "2.2.2",
        "com.github.nscala-time" %% "nscala-time" % "1.0.0"
      )
    )
  ) dependsOn(macros)
}