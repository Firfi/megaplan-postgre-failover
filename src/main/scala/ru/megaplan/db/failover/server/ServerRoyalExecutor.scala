package ru.megaplan.db.failover.server

import config.ApplicationConfig
import message._
import message.MasterElectedAddressMessage
import message.WatcherInitMessage
import org.apache.zookeeper._
import actors.Actor
import ru.megaplan.db.failover.{NodeConstants, RoyalExecutor}
import data.{Stat, ACL}
import scala.collection.JavaConversions._
import org.apache.zookeeper.AsyncCallback.{Children2Callback, StringCallback, DataCallback, StatCallback}
import org.apache.zookeeper.ZooDefs.Perms._
import ru.megaplan.db.failover.util.LogHelper
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import java.util
import org.apache.zookeeper.KeeperException.{SessionExpiredException, Code}

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 13.09.12
 * Time: 12:24
 * To change this template use File | Settings | File Templates.
 */
class ServerRoyalExecutor(val applicationConfig: ApplicationConfig) extends Watcher with Actor with RoyalExecutor with LogHelper {

  val MIN_NODES_FOR_START = 3

  val waitForStartCallback = new Children2Callback with Watcher { // we can use it as single-threaded 'cause async callbacks use one thread

    var initialized = false

    def processResult(rc: Int, path: String, ctx: Any, children: util.List[String], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case Code.OK => {
          if (children.size() >= MIN_NODES_FOR_START) {
            initialized = true
            royalExecutor ! new RoyalExecutorInitMessage
          }
        }
        case _ => {
          throw new RuntimeException("can't find servers node")
        }
      }
    }

    def process(e: WatchedEvent) {
      if (!initialized) {
        if (e.getType == EventType.NodeChildrenChanged) {
          royalExecutor ! new WaitForStartMessage
        }
      }
    }
  }

  def process(e: WatchedEvent) {
    println("generic event : " + e)
    e.getState match {
      case KeeperState.SyncConnected => {
        this ! new InitMasterElectActorMessage
      }
      case KeeperState.Disconnected => {
        log.warn("distonnected event")
      }
      case KeeperState.Expired => {
        log.warn("session expired!")
        this ! new SessionExpiredMessage
      }
    }
  }


  def act() {

    var continue = true

    var zk = new ZooKeeper(applicationConfig.zooConnectString, 3000, this)
    var initMessage = new WatcherInitMessage(zk, this)

    masterChangedWatcherActor.start()
    masterElectActor.start()

    loopWhile(continue) {
      receive {
        case m: SessionExpiredMessage => {
          zk = new ZooKeeper(applicationConfig.zooConnectString, 3000, this)
          initMessage = new WatcherInitMessage(zk, this)
          this ! new RoyalExecutorInitMessage
        }
        case m: WaitForStartMessage => {
          zk.getChildren(NodeConstants.SERVERS_ROOT, waitForStartCallback, waitForStartCallback, null)
        }
        case m: InitMasterElectActorMessage => {
          val reply = masterElectActor !? initMessage // we make our child node for servers root here and don't want any actions before initialisation
          if (reply == false) {
            Thread.sleep(3000)
            this ! m
          } else {
            log.debug("end'o masterElectActor initialisation")
            this ! new WaitForStartMessage
          }// again
        }
        case m: RoyalExecutorInitMessage => {
          masterChangedWatcherActor ! initMessage
        }
        case m: MasterDeletedMessage => {
          masterElectActor ! new MasterElectMessage
        }
        case MasterElectedAddressMessage(newMasterAddress) => {
          log.debug("comparing new master : " + newMasterAddress + " with my myDbAddress : " + applicationConfig.dbAddress)
          if (applicationConfig.dbAddress == newMasterAddress) {
            log.debug("new elected master is me so create ephemeral master node with value : " + newMasterAddress)
            //TODO: but first create trigger if i am slave now
            val currentMasterAndTriggerValueOption = configUtil.getMasterAndTrigger(applicationConfig.dbPath)
            log.warn(currentMasterAndTriggerValueOption)
            if (!currentMasterAndTriggerValueOption.isEmpty && currentMasterAndTriggerValueOption.get._1 != newMasterAddress) { //also master changed and we switch from slave
              log.info("i wasn't master so create trigger file : ")
              new java.io.File(currentMasterAndTriggerValueOption.get._2).createNewFile()
            }
            zk.create(
              NodeConstants.MASTER_NODE,
              newMasterAddress.getBytes,
              List(new ACL(CREATE|DELETE|WRITE|READ,ZooDefs.Ids.ANYONE_ID_UNSAFE)),
              CreateMode.EPHEMERAL
            )
          } else {
            log.debug("we soon will have new master, just wait")
            masterChangedWatcherActor ! new StartWatchMessage
          }
        }
      }
    }
  }
}
