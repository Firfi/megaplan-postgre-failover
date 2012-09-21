package ru.megaplan.db.failover2.server

import actors.Actor
import ru.megaplan.db.failover.server.config.ApplicationConfig
import org.apache.zookeeper.{AsyncCallback, WatchedEvent, Watcher, ZooKeeper}
import ru.megaplan.db.failover.util.LogHelper
import ru.megaplan.db.failover.NodeConstants
import org.apache.zookeeper.AsyncCallback.DataCallback
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import ru.megaplan.db.failover.server.config.util.ConfigUtil

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 17:50
 * To change this template use File | Settings | File Templates.
 */
class FailoverActor(applicationConfig: ApplicationConfig, zk: ZooKeeper) extends Actor with LogHelper {

  val configUtil: ConfigUtil = new ConfigUtil(applicationConfig)

  val masterWatcher = new Watcher {
    def process(e: WatchedEvent) {

    }
  }

  val masterValueCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case Code.OK => {
          val newMasterValue = new String(data)
          val masterAndTrigger: Option[(String, String)] = configUtil.getMasterAndTrigger
          if (applicationConfig.dbAddress == newMasterValue) {  //it is me
            masterAndTrigger match {
              case None => {

              } // assume I am master now
              case Some => {

              }
            }
          } else {

          }
        }
        case Code.NONODE => {

        }
        case _ => {}
      }
    }
  }

  def act() {
    log.debug("starting new failover actor for session " + zk.getSessionId)
    zk.getData(NodeConstants.MASTER_NODE, masterWatcher, masterValueCallback, null)
  }
}
