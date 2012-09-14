package ru.megaplan.db.failover.server

import actors.Actor
import message._
import message.WatcherInitMessage
import org.apache.zookeeper.{ZooKeeper, WatchedEvent, Watcher}
import ru.megaplan.db.failover.NodeConstants
import NodeConstants.MASTER_NODE
import org.apache.zookeeper.AsyncCallback.{DataCallback, StatCallback}
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.KeeperException.Code._

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 14.09.12
 * Time: 13:01
 * To change this template use File | Settings | File Templates.
 */
object masterChangedWatcherActor extends Actor with Watcher {

  var myMasterAddress: String = null
  var myDbAddress: String = null

  val initCheckExistsCallback = new StatCallback {
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
      val code = Code.get(rc)
      code match {
        case OK => { //if exists then get value and pass to daddy as init message
          println("master exists on init : " + path)
          masterChangedWatcherActor ! new MasterExistsMessage
        }
        case NONODE => { //if do not exists then pass daddy master not exists message
          println("master not exists on init : " + path)
          masterChangedWatcherActor ! new MasterDeletedMessage
        }
        case _ => {}  //if error ignore, daddy will resolve all problems
      }
    }
  }

  val refreshCheckExistsCallback = new StatCallback { // assume that on point of calling this callback master has to exists, else we assume that it was deleted
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
      val code = Code.get(rc)
      code match {
        case NONODE => { //if not exists pass to daddy _DELETED_ message

        }
        case _ => {} //any else just ignore, we are in Night Watch
      }
    }
  }

  val masterValueCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case OK => {
          val masterValue = new String(data)
          if (masterValue == myMasterAddress) {  // i have this master in config
            println("i am master so don't do anynting")
          } else { // i don't have this master in config
            println("setting new master : " + masterValue)
            myMasterAddress = masterValue
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
      case _ => {} //daddy will resolve other problems
    }

  }

  def act() {

    var zk: ZooKeeper = null
    var daddy: ServerRoyalExecutor = null


    loop {
      receive {
        case WatcherInitMessage(zooKeeper, serverRoyalExecutor) => {
          println("initializing masterChangedWatcherActor")
          zk = zooKeeper
          daddy = serverRoyalExecutor
          myDbAddress = daddy.myDbAddress
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
