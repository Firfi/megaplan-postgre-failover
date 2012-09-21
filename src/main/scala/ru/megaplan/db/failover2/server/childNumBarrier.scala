package ru.megaplan.db.failover2.server

import message.ChildBarrierOk
import org.apache.zookeeper.{WatchedEvent, Watcher, ZooKeeper}
import org.apache.zookeeper.AsyncCallback.Children2Callback
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.Watcher.Event.{KeeperState, EventType}
import ru.megaplan.db.failover.NodeConstants
import ru.megaplan.db.failover.util.LogHelper
import java.util
import actors.Actor

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 19:39
 * To change this template use File | Settings | File Templates.
 */
object childNumBarrier extends LogHelper {

  val MIN_CHILD_NUM = 3

  def waitChildNumBarrier(zk: ZooKeeper, responseActor: Actor) {

    var barrierRan = false

    def getChildNum {
      zk.getChildren(NodeConstants.SERVERS_ROOT, startApplicationBarrierWatcher, childrenNumCallback, null)
    }

    def childNumIsOk {
      if (!barrierRan) {
        barrierRan = true
        responseActor ! new ChildBarrierOk
      } else {
        log.warn("barrier ran once, ignore")
      }
    }

    getChildNum

    def childrenNumCallback = new Children2Callback {
      def processResult(rc: Int, path: String, ctx: Any, children: util.List[String], stat: Stat) {
        val code = Code.get(rc)
        code match {
          case Code.OK => {
            val childNum = children.size()
            if (childNum == 0 || childNum == 1) {
              throw new RuntimeException("child num is " + childNum + " it is illegal state") // this cant'be
            }
            if (childNum < MIN_CHILD_NUM) {
              log.debug("child num < MIN_CHILD_NUM so watch for children")
            } else {
              childNumIsOk
            }
          }
          case Code.NONODE => {
            log.error("can't find servers node, create it manually please. exiting...")
            System.exit(0)
          }
          case Code.SESSIONEXPIRED => {
            log.debug("session expired here")
          }
          case _ => {
            log.warn("some other response : " + code + " for node : " + path)
          }
        }
      }
    }
    def startApplicationBarrierWatcher = new Watcher {
      def process(e: WatchedEvent) {
        if (barrierRan) return
        e.getType match {
          case EventType.NodeChildrenChanged => {
            getChildNum
          }
          case _ => {
            e.getState match {
              case KeeperState.Expired => {log.debug("session expired in startApplicationBarrierWatcher")}
              case KeeperState.Disconnected => {
                log.debug("disconnected in startApplicationBarrierWatcher; watch again...")
                Thread.sleep(2000)
                getChildNum
              }
            }
          }
        }
      }
    }
  }
}
