package ru.megaplan.db.failover.server.config

import com.typesafe.config.Config

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.09.12
 * Time: 13:46
 * To change this template use File | Settings | File Templates.
 */
class ApplicationConfig(config: Config) {
  val dbPath = config.getString("dbPath")
  val dbAddress = config.getString("dbAddress")
  val myId = config.getInt("myId")
  val zooConnectString = config.getString("zooConnectString")
  val restartScript = config.getString("restartScript")
  val masterPullScript = config.getString("masterPullScript")
  val promoteScript = config.getString("promoteScript")
}
