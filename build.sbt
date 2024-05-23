ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    name := "VerteileSystemePraktikum"
  )

libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.3"
libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.8.5"
libraryDependencies += "com.typesafe.akka" %% "akka-persistence-typed" % "2.8.5"

val circeVersion = "0.15.0-M1"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

