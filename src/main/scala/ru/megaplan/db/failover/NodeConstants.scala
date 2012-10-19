package ru.megaplan.db.failover

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 13.09.12
 * Time: 12:37
 * To change this template use File | Settings | File Templates.
 */
object NodeConstants {
  val MASTER_NODE = "/db/failover/master"
  val SERVERS_ROOT = "/db/failover/servers"
  val SHUTDOWN_SIG = "/db/failover/shutdown"
  val PAUSE_SIG = "/db/failover/pause"
  val MASTER_TIMELINE = "/db/failover/timeline"
}
