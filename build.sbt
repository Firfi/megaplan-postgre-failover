import sbtassembly.Plugin._

name := "A Project"

version := "0.1"

scalaVersion := "2.9.1"

resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
  "org.apache.zookeeper" % "zookeeper" % "3.4.3" exclude("javax.jms", "jms") exclude("com.sun.jdmk","jmxtools") exclude("com.sun.jmx","jmxri") ,
  "commons-lang" % "commons-lang" % "2.2",
  "commons-io" % "commons-io" % "2.0",
  "com.typesafe" % "config" % "0.5.2",
  "com.codahale" % "logula_2.9.1" % "2.1.3"
)

seq(assemblySettings: _*)