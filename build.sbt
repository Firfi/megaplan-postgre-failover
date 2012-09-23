import sbtassembly.Plugin._
import AssemblyKeys._

name := "gospodin-pg"

version := "0.1"

scalaVersion := "2.9.1"

resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
  "org.apache.zookeeper" % "zookeeper" % "3.4.3" exclude("javax.jms", "jms") exclude("com.sun.jdmk","jmxtools") exclude("com.sun.jmx","jmxri") ,
  "com.typesafe" % "config" % "0.5.2",
  "com.codahale" % "logula_2.9.1" % "2.1.3"
)

//jarName in assembly :=
mainClass in assembly := Some("ru.megaplan.db.failover2.server.application")

seq(assemblySettings: _*)