package ru.megaplan.db.failover.client

import org.apache.zookeeper.{ZooKeeper, WatchedEvent, Watcher}
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.AsyncCallback.{StatCallback, DataCallback}
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import ru.megaplan.db.failover.message.{GenericMasterWatcherMessage, MasterDeletedMessage, MasterChangedMessage, ClientInitMasterMessage}
import ru.megaplan.db.failover.{DbConstants, RoyalExecutor, NodeConstants}
import actors.Actor
import com.codahale.logula.Logging

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 12.09.12
 * Time: 12:19
 * To change this template use File | Settings | File Templates.
 */
class ClientRoyalExecutor(val hostPort: String, val shell: String) extends Actor with Logging {

  val royalExecutor = this

  val clientConnectionWatcher = new Watcher {
    def process(e: WatchedEvent) {
      royalExecutor ! ClientInitMasterMessage
    }
  }

  val clientMasterWatcher = new Watcher {
    def process(e: WatchedEvent) {
      e.getType match {
        case EventType.NodeCreated | EventType.NodeDataChanged => {
          royalExecutor ! MasterChangedMessage
        }
        case EventType.NodeDeleted => {
          royalExecutor ! MasterDeletedMessage
        }
        case _ => {
          e.getState match {
            case KeeperState.Expired | KeeperState.Disconnected => {
              // do nothing
            }
            case _ => {
              royalExecutor ! GenericMasterWatcherMessage
            }
          }
        }
      }
    }
  }

  val clientMasterDataCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case Code.NONODE => {}
        case Code.OK => {
          royalExecutor ! MasterChangedMessage
        }
        case _ => {}
      }
    }
  }



  def act() {
    var zk = new ZooKeeper(hostPort, 3000, clientConnectionWatcher)
    zk.exists(NodeConstants.MASTER_NODE, clientMasterWatcher)
    loop {
      receive {
        case ClientInitMasterMessage => {
          zk = new ZooKeeper(hostPort, 3000, clientConnectionWatcher)
          zk.getData(NodeConstants.MASTER_NODE, false, clientMasterDataCallback, null)
        }
        case MasterChangedMessage => {
          zk.getData(NodeConstants.MASTER_NODE, false, clientMasterDataCallback, null)
        }
        case MasterDeletedMessage | GenericMasterWatcherMessage=> {
          zk.exists(NodeConstants.MASTER_NODE, clientMasterWatcher)
        }
      }
    }

  }

}
