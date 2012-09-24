package ru.megaplan.db.failover.client

import org.apache.zookeeper.{AsyncCallback, ZooKeeper, WatchedEvent, Watcher}
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.AsyncCallback.{VoidCallback, StatCallback, DataCallback}
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import ru.megaplan.db.failover.message._
import ru.megaplan.db.failover.{DbConstants, RoyalExecutor, NodeConstants}
import actors.Actor
import com.codahale.logula.Logging
import ru.megaplan.db.failover.message.MasterChangedMessage
import ru.megaplan.db.failover.message.ClientInitMasterMessage
import io.Source
import java.io.FileOutputStream

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 12.09.12
 * Time: 12:19
 * To change this template use File | Settings | File Templates.
 */
class ClientRoyalExecutor(val hostPort: String, val shell: String) extends Actor with Logging {

  val syncCallback = new VoidCallback {
    def processResult(rc: Int, path: String, ctx: Any) {
      watchMaster
    }
  }

  val mainConnectionWatcher = new Watcher {
    var afterExpired = false
    def process(e: WatchedEvent) {
      log.debug("connection event : " + e)
      e.getType match {
        case EventType.None => {
          e.getState match {
            case KeeperState.SyncConnected => {
              if (afterExpired) {
                afterExpired = false
                zk.sync(NodeConstants.MASTER_NODE, syncCallback, null)
              } else {
                watchMaster
              }

            }
            case KeeperState.Expired => {
              afterExpired = true
              initZk
            }
            case KeeperState.Disconnected => {
              //wait for expired event
            }
            case _ => {
              log.warn("some other event type")
            }
          }
        }
        case _ => {
          log.warn("this can't be : " + e)
        }
      }
    }
  }

  val masterExistenceCallback = new StatCallback {
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
      val code = Code.get(rc)
      code match {
        case Code.OK => {
          getMasterData
        }
        case Code.NONODE => {
          log.warn("master not exists in masterExistenceCallback")
        }
        case _ => {}
      }
    }
  }

  val masterWatcher = new Watcher {
    def process(e: WatchedEvent) {
      e.getState match {
        case KeeperState.SyncConnected => {
          e.getType match {
            case EventType.NodeCreated => {
              getMasterData
            }
            case EventType.NodeDeleted => {
              watchMaster
            }
            case _ => {
              log.warn("illegal master node event type with event : " + e)
            }
          }
        }
        case _ => {
          log.debug("bad event : " + e + " in masterWatcher")
        }
      }
    }
  }

  val masterDataCallback = new DataCallback {

    def setMaster(addressAndPort: String) {
      val newContent = Source.fromFile(shell).getLines().map(line => {
        if (line.contains("host=")) line.replaceFirst("host=[^ ]+","host="+addressAndPort.split(':')(0)) else line
      }).mkString("\n").getBytes
      val fos = new FileOutputStream(shell)
      fos write newContent
      fos.close()
    }

    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case Code.OK => {
          setMaster(new String(data))
          log.warn("new master : " + new String(data))
        }
        case Code.NONODE => {
          log.error("no node on master data callback")
        }
        case _ => {
          log.warn("other code in masterdatacallback : " + code)
        }
      }
    }
  }

  var zk: ZooKeeper = null
  def initZk {zk = new ZooKeeper(hostPort, 3000, mainConnectionWatcher, true)}
  def watchMaster {zk.exists(NodeConstants.MASTER_NODE, masterWatcher, masterExistenceCallback, null)}
  def getMasterData {zk.getData(NodeConstants.MASTER_NODE, masterWatcher, masterDataCallback, null)}

  def act() {
    initZk
    loop {

    }
  }

}
