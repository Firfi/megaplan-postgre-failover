package ru.megaplan.db.failover2.server

import actors.Actor
import message.{MyChildNodeDisappear}
import org.apache.zookeeper.{WatchedEvent, Watcher, ZooKeeper}
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}
import org.apache.zookeeper.AsyncCallback.StatCallback
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import ru.megaplan.db.failover.NodeConstants
import com.codahale.logula.Logging

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 19:23
 * To change this template use File | Settings | File Templates.
 */
object childInitializer extends Logging {

  def watchChildMe(zk: ZooKeeper, actorForResponse: Actor, myId: Int) = new {

    var responceOk = false

    def childDisappear {
      if (!responceOk) {
        responceOk = true // perhaps childMeStatCallback would run this method first
        actorForResponse ! MyChildNodeDisappear(zk)
      }

    }
    def watch {
      zk.exists(NodeConstants.SERVERS_ROOT+"/"+myId, childMeWatcher, childMeStatCallback, null)
    }

    watch

    val childMeWatcher = new Watcher {
      def process(e: WatchedEvent) {
        e.getType match {
          case EventType.NodeDeleted => {
            childDisappear
          }
          case EventType.NodeDataChanged => {
            // this can't be in current logic
          }
          case EventType.None => {
            e.getState match {
              case KeeperState.Expired => {
                log.debug("we expired here but main watcher will resolve this issue")
              }
              case KeeperState.Disconnected => {
                log.debug("disconnected, watch again")
                watch
              }
              case _ => {
                log.error("some other problem in init child watch : " + e + " init again after some time")
                Thread.sleep(2000)
                watch
              }
            }
          }
          case _ => {
            log.debug("some other status in event : " + e + " watch again")
            watch
          }
        }
      }
    }
    val childMeStatCallback = new StatCallback {
      def processResult(rc: Int, path: String, ctx: Any, stat: Stat) {
        val code = Code.get(rc)
        code match {
          case Code.OK => { //it is ok, we wait child here
            log.debug("waiting for child disappear...")
          }
          case Code.NONODE => { //mayhaps child suddenly died
            childDisappear
          }
          case _ => {
            log.warn("some other code on aquiring child status : " + code)
          }
        }
      }
    }

  }
}
