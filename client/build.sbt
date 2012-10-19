import sbtassembly.Plugin._
import AssemblyKeys._

name := "slave_pg"

version := "0.1"

scalaVersion := "2.9.1"

resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
  "org.apache.zookeeper" % "zookeeper" % "3.4.3" exclude("log4j", "log4j") exclude("javax.jms", "jms") exclude("com.sun.jdmk", "jmxtools") exclude("com.sun.jmx", "jmxri") exclude("junit", "junit"),
  "com.typesafe" % "config" % "0.5.2",
  "com.codahale" % "logula_2.9.1" % "2.1.3",
  "org.scalatest" %% "scalatest" % "1.8" % "test",
  "junit" % "junit" % "4.10" % "test"
)

//jarName in assembly :=
mainClass := Some("ru.megaplan.db.failover.client.ClientApp")

seq(assemblySettings: _*)