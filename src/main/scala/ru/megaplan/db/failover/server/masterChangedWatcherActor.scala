package ru.megaplan.db.failover.server

import actors.Actor
import config.ApplicationConfig
import config.util.{ConfigUtil}
import message._
import message.WatcherInitMessage
import org.apache.zookeeper.{ZooKeeper, WatchedEvent, Watcher}
import ru.megaplan.db.failover.NodeConstants
import NodeConstants.MASTER_NODE
import org.apache.zookeeper.AsyncCallback.{DataCallback, StatCallback}
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.KeeperException.Code._
import com.codahale.logula.Logging

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 14.09.12
 * Time: 13:01
 * To change this template use File | Settings | File Templates.
 */
object masterChangedWatcherActor extends Actor with Watcher with Logging {

  var applicationConfig: ApplicationConfig = null
  var myMasterAddress: Option[String] = Option.empty
  var configUtil: ConfigUtil = null

  val initCheckExistsCallback = new StatCallback with Logging {
    log.debug("starting initCheckExistsCallback")
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
      val code = Code.get(rc)
      code match {
        case OK => { //if exists then get value and pass to daddy as init message
          log.info("master exists on init : " + path)
          masterChangedWatcherActor ! new MasterExistsMessage
        }
        case NONODE => { //if do not exists then pass daddy master not exists message
          log.info("master not exists on init : " + path)
          masterChangedWatcherActor ! new MasterDeletedMessage
        }
        case CONNECTIONLOSS => {
          masterChangedWatcherActor ! new WatchAgainMessage
        }  //if error ignore, daddy will resolve all problems
      }
    }
  }

  val refreshCheckExistsCallback = new StatCallback with Logging { // assume that on point of calling this callback master has to exists, else we assume that it was deleted
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
      val code = Code.get(rc)
      code match {
        case NONODE => { //if not exists pass to daddy _DELETED_ message
          masterChangedWatcherActor ! new MasterDeletedMessage
        }
        case CONNECTIONLOSS => {
          masterChangedWatcherActor ! new WatchAgainMessage
        }
        case _ => {
          log.debug("other code : " + code)
        }
      }
    }
  }

  val masterValueCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case OK => {
          val masterValue = new String(data)
          if (myMasterAddress.isEmpty || myMasterAddress.get != masterValue) {
            log.info("setting new master : " + masterValue)
            myMasterAddress = Option(masterValue)
            if (applicationConfig.dbAddress != masterValue) { //and it is not ME
              configUtil.setRecoveryMode
              configUtil.setCurrentMasterValue(masterValue)
              configUtil.restartDb
            }
          } else {   // i have this master in config
            log.info("i am master or master is set so don't do anynting ") //but maybe check for recovery.conf and remove it
          }
        }
        case NONODE => {
          masterChangedWatcherActor ! new MasterDeletedMessage
        }
        case _ => {} //general errors will be resolved by daddy
      }
      masterChangedWatcherActor ! new WatchAgainMessage //rewatch anyway
    }
  }

  def process(e: WatchedEvent) {
    import org.apache.zookeeper.Watcher.Event.EventType._
    e.getType match {
      case NodeCreated => {
        masterChangedWatcherActor ! new MasterCreatedMessage
      }
      case NodeDataChanged => {
        masterChangedWatcherActor ! new MasterChangedMessage
      }
      case NodeDeleted => {
        masterChangedWatcherActor ! new MasterDeletedMessage
      }
      case _ => {
        log.debug("other type in masterChangedWatcherActor proccess method : " + e.getType + " and state : " + e.getState)
      } //daddy will resolve other problems
    }

  }

  def act() {

    var zk: ZooKeeper = null
    var daddy: ServerRoyalExecutor = null


    loop {
      receive {
        case WatcherInitMessage(zooKeeper, serverRoyalExecutor) => {
          log.debug("initializing masterChangedWatcherActor")
          zk = zooKeeper
          daddy = serverRoyalExecutor

          applicationConfig = serverRoyalExecutor.applicationConfig
          configUtil = new ConfigUtil(applicationConfig)
          myMasterAddress = configUtil.getCurrentMasterValue
          log.debug("current master address from config util : " + myMasterAddress)
          this ! new StartWatchMessage
        }
        case m: StartWatchMessage => {
          zk.exists(MASTER_NODE, this, initCheckExistsCallback, null)
        }
        case m: WatchAgainMessage => {
          zk.exists(MASTER_NODE, this, refreshCheckExistsCallback, null)
        }
        case m: MasterExistsMessage => {
          zk.getData(MASTER_NODE, this, masterValueCallback, null)
        }
        case m: MasterCreatedMessage => {
          zk.getData(MASTER_NODE, this, masterValueCallback, null)
        }
        case m: MasterChangedMessage => {
          zk.getData(MASTER_NODE, this, masterValueCallback, null)
        }
        case m: MasterDeletedMessage => {
          daddy ! m
        }
      }
    }
  }

}
