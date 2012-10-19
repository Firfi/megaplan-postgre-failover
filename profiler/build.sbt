import sbtassembly.Plugin._
import AssemblyKeys._

name := "profiler_pg"

version := "0.1"

scalaVersion := "2.9.1"

resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
  "com.codahale" % "logula_2.9.1" % "2.1.3",
  "postgresql" % "postgresql" % "9.1-901.jdbc4",
  "org.scalatest" %% "scalatest" % "1.8" % "test",
  "junit" % "junit" % "4.10" % "test"
)