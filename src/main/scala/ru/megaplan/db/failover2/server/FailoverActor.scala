package ru.megaplan.db.failover2.server

import actors.Actor
import message._
import message.PullMasterMessage
import message.PullMasterMessage
import message.PullMasterMessage
import ru.megaplan.db.failover.server.config.ApplicationConfig
import org.apache.zookeeper._
import ru.megaplan.db.failover.util.LogHelper
import ru.megaplan.db.failover.NodeConstants
import org.apache.zookeeper.AsyncCallback.{Children2Callback, DataCallback}
import org.apache.zookeeper.data.{ACL, Stat}
import org.apache.zookeeper.KeeperException.Code
import ru.megaplan.db.failover.server.config.util.ConfigUtil
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}
import java.util
import EventType._
import org.apache.zookeeper.ZooDefs.Perms._
import scala.Some
import scala.collection.JavaConversions._

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 17:50
 * To change this template use File | Settings | File Templates.
 */
class FailoverActor(applicationConfig: ApplicationConfig, zk: ZooKeeper) extends Actor with LogHelper {

  val failoverActor = this

  val configUtil: ConfigUtil = new ConfigUtil(applicationConfig)

  val masterWatcher = new Watcher {
    def process(e: WatchedEvent) {
      e.getType match {
        case NodeCreated => {
          failoverActor ! MasterCreatedMessage
        }
        case NodeDeleted => {
          failoverActor ! MasterDeletedMessage
        }
      }
    }
  }

  val masterValueCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
        Code.get(rc) match {
        case Code.OK => {
          val newMasterValue = new String(data)
          val masterAndTrigger: Option[(String, String)] = configUtil.getMasterAndTrigger
          if (applicationConfig.dbAddress == newMasterValue) {  //it is me
            masterAndTrigger match {
              case None => {
                log.debug("recovery.conf is deleted, assume i am master so ignore that")
              }
              case Some(dbConfigMaster) => { //assume i am slave right now
                if (newMasterValue == dbConfigMaster) {
                  // and i am master in recovery.conf so that can't be
                  log.error("i am master new master in master node but in my recovery.conf too, some error here")
                }
                configChangeQueue ! PromoteMessage
              }
            }
          } else { // some other master
            masterAndTrigger match {
              case None => { // and recovery.conf deleted so assume i am master in config
                configChangeQueue ! PullMasterMessage
              }
              case Some(dbConfigMaster) => { // i am slave right now
                if (newMasterValue == dbConfigMaster) {
                  log.debug("new master in my settings right now, just ignore")
                } else {
                  configChangeQueue ! PullMasterMessage
                }
              }
            }
          }
        }
        case Code.NONODE => {

        }
        case _ => {}
      }
    }
  }

  def initMasterWatcher = zk.getData(NodeConstants.MASTER_NODE, masterWatcher, masterValueCallback, null)


  def act() {
    log.debug("starting new failover actor for session " + zk.getSessionId)

    receive {
      case FailoverActorTerminateMessage => {}//TODO terminate
      case MasterCreatedMessage => {
        initMasterWatcher
      }
      case MasterDeletedMessage => {
        masterElector.electMaster(applicationConfig, zk, this)
      }
      case IAmMasterNowMessage => {
        zk.create(
          NodeConstants.MASTER_NODE,
          applicationConfig.dbAddress.getBytes,
          List(new ACL(CREATE|DELETE|WRITE|READ,ZooDefs.Ids.ANYONE_ID_UNSAFE)),
          CreateMode.EPHEMERAL
        )
      }
    }
  }



}
