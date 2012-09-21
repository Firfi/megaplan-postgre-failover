package ru.megaplan.db.failover2.server

import message._
import ru.megaplan.db.failover.server.config.ApplicationConfig
import actors.Actor
import org.apache.zookeeper._
import data.Stat
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import ru.megaplan.db.failover.util.LogHelper
import ru.megaplan.db.failover.NodeConstants
import org.apache.zookeeper.KeeperException.{NoNodeException, Code, NodeExistsException}
import org.apache.zookeeper.AsyncCallback.{Children2Callback, StatCallback}
import java.util

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 19.09.12
 * Time: 20:42
 * To change this template use File | Settings | File Templates.
 */
class RoyalExecutor(val applicationConfig: ApplicationConfig) extends Actor with LogHelper {

  def mainConnectionWatcher = new Watcher {
    def process(e: WatchedEvent) {
      e.getState match {
        case KeeperState.Expired => {
          log.info("session expired")

        }
        case _ => {
          log.debug("generic connection event : " + e)
        }
      }
    }
  }



  def initZkConnection = {
    new ZooKeeper(applicationConfig.zooConnectString, 3000, mainConnectionWatcher)
  }

  def createChildMe(zk: ZooKeeper): Boolean = {
    try {
      zk.create(
        NodeConstants.SERVERS_ROOT+"/"+applicationConfig.myId,
        applicationConfig.dbAddress.toCharArray.map(_.toByte),
        ZooDefs.Ids.READ_ACL_UNSAFE,
        CreateMode.EPHEMERAL
      )
      true
    } catch {
      case e: NodeExistsException => {
        log.error("child node : " + applicationConfig.myId + " exists")
      }
      case e: Exception => {
        log.error("some exception in child node creation",e)
      }
    }
    false
  }

  def act() {

    var continue = true
    val zk = initZkConnection

    val created = createChildMe(zk)
    if (!created) {
      log.error("child node exists on application start - wrong child node in config perhaps")
      System.exit(0)
    }

    childNumBarrier.waitChildNumBarrier(zk, this)
    log.debug("waiting for child num barrier... ")
    receive {
      case m: ChildNumBarrierMessage => {
        case m: ChildBarrierOk => {
          log.info("child num barrier returns ok, starting failover workflow")
          new FailoverActor(applicationConfig, zk).start()
        }
      }
    }

    loopWhile(continue) {
      receive {
        case m: ApplicationWorkflowMessage => {
          case m : ReinitApplicationMessage => {
            val zk = initZkConnection
            val created = createChildMe(zk)
            if (!created) {
              log.warn("my child node exists on reinit, watching for node deletion...")
              childInitializer.watchChildMe(zk, this, applicationConfig.myId) // and wait for child init
            } else {
              new FailoverActor(applicationConfig, zk).start()
            }
          }
          case m: StopApplicatiomMessage => {
            continue = false
          }
        }
        case m: ChildInitWatcherMessage => {
          case ChildDisappear(activeZooKeeper) => {
            new FailoverActor(applicationConfig, activeZooKeeper).start()
          }
        }

      }
    }
  }

}
