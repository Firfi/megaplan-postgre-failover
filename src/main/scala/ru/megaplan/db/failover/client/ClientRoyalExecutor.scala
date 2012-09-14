package ru.megaplan.db.failover.client

import org.apache.zookeeper.{ZooKeeper, WatchedEvent, Watcher}
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.AsyncCallback.{StatCallback, DataCallback}
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import ru.megaplan.db.failover.message.{ClientInitMasterMessage}
import ru.megaplan.db.failover.{RoyalExecutor, NodeConstants}

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 12.09.12
 * Time: 12:19
 * To change this template use File | Settings | File Templates.
 */
class ClientRoyalExecutor(val hostPort: String, val shell: String) extends RoyalExecutor {


  val PING_TIMEOUT = 2000
  var zk: ZooKeeper = new ZooKeeper(hostPort, MAIN_TIMEOUT, royalExecutor)

  val generalStatCallback = new StatCallback {
    var lastChangeId = 0L
    var lastCreateId = 0L
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
      val code = Code.get(rc)
      code match {
        case Code.OK => {
          if (stat.getCzxid != lastCreateId || stat.getMzxid > lastChangeId) {
            // TODO maybe it isn't necessary
            lastCreateId = stat.getCzxid
            lastChangeId = stat.getMzxid
            zk.getData(path, masterNodeChangeWatcher, masterChangedHandler, null)
          } else {
            Thread.sleep(PING_TIMEOUT)
            royalExecutor ! new ClientInitMasterMessage(this)
          }
        }
        case Code.NONODE => {
          println("can't find master node")
          Thread.sleep(PING_TIMEOUT)
          royalExecutor ! new ClientInitMasterMessage(this)
        }
        case Code.SESSIONEXPIRED => {
          zk = new ZooKeeper(hostPort, MAIN_TIMEOUT, royalExecutor)
          royalExecutor ! new ClientInitMasterMessage(this)
        }
        case _ => {
          println("some other error: " + Code.get(rc))
        }
      }
    }
  }

  val masterNodeChangeWatcher = new Watcher {
    def process(e: WatchedEvent) {
      e.getType match {
        case EventType.NodeDataChanged => {
          zk.getData(NodeConstants.MASTER_NODE, this, masterChangedHandler, null)
        }
        case EventType.NodeDeleted => {
          royalExecutor ! new ClientInitMasterMessage(this)
        }
        case _ => royalExecutor ! new ClientInitMasterMessage(this)
      }
    }
  }
  val masterChangedHandler = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      println("changing master in config here; data : " + new String(data))
      // TODO check for ME and call script if neccessary, reinit all if error
    }
  }

  def process(e: WatchedEvent) {
    // TODO here we process general connection troubles
  }

  def act() {
    def initMaster {
      zk.exists(NodeConstants.MASTER_NODE, true, generalStatCallback, null)
    }
    initMaster
    loop {
      receive {
        case ClientInitMasterMessage(a) => {
          println("received init master message")
          initMaster
        }
      }
    }
  }


}
