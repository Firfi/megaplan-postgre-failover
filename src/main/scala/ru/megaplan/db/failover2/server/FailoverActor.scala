package ru.megaplan.db.failover2.server

import actors.Actor
import message._
import message.PullMasterMessage
import ru.megaplan.db.failover.server.config.ApplicationConfig
import org.apache.zookeeper._
import ru.megaplan.db.failover.NodeConstants
import org.apache.zookeeper.AsyncCallback.{StatCallback, Children2Callback, DataCallback}
import org.apache.zookeeper.data.{ACL, Stat}
import org.apache.zookeeper.KeeperException.Code
import ru.megaplan.db.failover.server.config.util.ConfigUtil
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}
import java.util
import org.apache.zookeeper.ZooDefs.Perms._
import scala.Some
import scala.collection.JavaConversions._
import com.codahale.logula.Logging

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 17:50
 * To change this template use File | Settings | File Templates.
 */
class FailoverActor(applicationConfig: ApplicationConfig, zk: ZooKeeper) extends Actor with Logging {

  val failoverActor = this

  val configUtil: ConfigUtil = new ConfigUtil(applicationConfig)

  val masterExistenceWatcher = new Watcher {
    def process(e: WatchedEvent) {
      log.debug("firing master existence watcher with event : " + e)
      import EventType._
      e.getType match {
        case NodeCreated => {
          failoverActor ! MasterCreatedMessage
        }
        case NodeDeleted => {
          failoverActor ! MasterDeletedMessage
        }
        case otherType => {
          log.debug("other event in masterWatcher : " + e)
          import KeeperState._
          e.getState match {
            case Expired => return
            case _ => {log.debug("other state and type with event : " + e)}
          }
        }
      }
      zk.exists(e.getPath, this, null, null)
    }
  }

  val masterStatCallback = new StatCallback {
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
      Code.get(rc) match {
        case Code.OK => {
          failoverActor ! MasterExistsMessage
        }
        case Code.NONODE => {
          log.debug("no master found")
          failoverActor ! NoMasterMessage
        }
        case other => {
          log.debug("other code in master existence callback : " + other + " and stat : " + stat)
        }
      }
    }
  }

  val masterValueCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      Code.get(rc) match {
        case Code.OK => {
          val newMasterValue = new String(data)
          log.debug("found master : " + newMasterValue)
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
                configChangeQueue ! PromoteMessage(applicationConfig, failoverActor)
              }
            }
          } else { // some other master
            masterAndTrigger match {
              case None => { // and recovery.conf deleted so assume i am master in config
                configChangeQueue ! PullMasterMessage(applicationConfig, newMasterValue)
              }
              case Some(dbConfigMaster) => { // i am slave right now
                if (newMasterValue == dbConfigMaster) {
                  log.debug("new master in my settings right now, just ignore")
                } else {
                  configChangeQueue ! PullMasterMessage(applicationConfig, newMasterValue)
                }
              }
            }
          }
        }
        case Code.NONODE => {
          log.debug("master deleted in proccess")
          failoverActor ! MasterDeletedMessage
        }
        case other => {
          log.debug("other code in master value callback : " + other + " and stat : " + stat)
        }
      }
    }
  }


  def act() {
    import NodeConstants._
    def initMasterExistenceWatcher = zk.exists(NodeConstants.MASTER_NODE, masterExistenceWatcher, masterStatCallback, null)
    def initMasterValueCallback = zk.getData(MASTER_NODE, false, masterValueCallback, null)
    log.debug("starting new failover actor for session " + zk.getSessionId)
    initMasterExistenceWatcher
    loop {
      receive {
        case FailoverActorTerminateMessage => {}//TODO terminate
        case MasterExistsMessage | MasterCreatedMessage => {
          initMasterValueCallback
        }
        case MasterDeletedMessage | NoMasterMessage => {
          MasterElector.electMaster(applicationConfig, zk, this)
        }
        case IAmMasterNowMessage => {
          zk.create(
            MASTER_NODE,
            applicationConfig.dbAddress.getBytes,
            List(new ACL(CREATE|DELETE|WRITE|READ,ZooDefs.Ids.ANYONE_ID_UNSAFE)),
            CreateMode.EPHEMERAL
          )
        }
        case MasterElectedMessage(master) => {
          log.debug("arriver masterElectedMessage : " + master)
          if (applicationConfig.dbAddress == master) {
            log.debug("master elected and it is me, so i am master now")
            this ! IAmMasterNowMessage
          }
        }
      }

    }
  }



}
