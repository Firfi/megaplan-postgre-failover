/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 03.10.12
 * Time: 13:56
 * To change this template use File | Settings | File Templates.
 */

import sbt._

object MyBuild extends Build {
  // Declare a project in the root directory of the build with ID "root".
  // Declare an execution dependency on sub1.
  lazy val root = Project("root", file("."))
  lazy val client = Project("slave_pg", file("client")) dependsOn(root, zkClient)
  lazy val server = Project("gospodin_pg", file("server")) dependsOn(root, zkClient)
  lazy val profiler = Project("profiler_pg", file("profiler")) dependsOn(root)
  lazy val zkClient = RootProject(uri("git://github.com/Firfi/scala-zookeeper-client.git")) // RootProject(file("/home/firfi/work/scala/scala-zookeeper-client/"))
}