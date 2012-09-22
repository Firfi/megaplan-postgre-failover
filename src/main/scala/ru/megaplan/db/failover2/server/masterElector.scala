package ru.megaplan.db.failover2.server

import message.MasterElectedMessage
import ru.megaplan.db.failover.server.config.ApplicationConfig
import org.apache.zookeeper.{WatchedEvent, Watcher, ZooKeeper}
import actors.Actor
import org.apache.zookeeper.Watcher.Event.EventType._
import org.apache.zookeeper.Watcher.Event.KeeperState._
import org.apache.zookeeper.AsyncCallback.{DataCallback, Children2Callback}
import java.util
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import util.Collections
import ru.megaplan.db.failover.NodeConstants
import ru.megaplan.db.failover.util.LogHelper

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 9/22/12
 * Time: 6:53 PM
 * To change this template use File | Settings | File Templates.
 */
object masterElector extends LogHelper {

  def electMaster(applicationConfig: ApplicationConfig, zk: ZooKeeper, caller: Actor) {

    var masterElected = false

    val electWatcher = new Watcher {
      def process(e: WatchedEvent) {
        if (masterElected) {
          log.debug("master elected already, so ignore watch event")
          return
        }
        e.getType match {
          case NodeChildrenChanged => {
            startElector
          }
          case _ => {
            e.getState match {
              case Expired => {}
              case _ => {
                startElector
              }
            }
          }
        }
      }
    }

    val electChildrenCallback = new Children2Callback {
      def processResult(rc: Int, path: String, ctx: Any, children: util.List[String], stat: Stat) {
        Code.get(rc) match {
          case Code.OK => {
            if (children.size > 0) {
              Collections.sort(children)
              zk.getData(path + "/" + children.get(0), false, electValueCallback, null)
            } else {
              log.error("children size is " + children.size + " for some reason, this can't be")
            }
          }
          case _ => {}
        }
      }
    }

    val electValueCallback = new DataCallback {
      def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
        Code.get(rc) match {
          case Code.OK => {
            masterElected = true
            caller ! MasterElectedMessage(new String(data))
          }
          case Code.NONODE => {
            log.warn("child node disapearred before election end, go again")
            startElector
          }
          case _ => {}
        }
      }
    }

    def startElector {
      zk.getChildren(NodeConstants.SERVERS_ROOT, electWatcher, electChildrenCallback, null)
    }

    startElector

  }
}
