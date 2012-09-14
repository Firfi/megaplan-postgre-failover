package ru.megaplan.db.failover.server

import message._
import message.MasterElectedMessage
import message.MasterElectedMessage
import message.WatcherInitMessage
import org.apache.zookeeper._
import actors.Actor
import ru.megaplan.db.failover.{NodeConstants, RoyalExecutor}
import data.{Stat, ACL}
import scala.collection.JavaConversions._
import org.apache.zookeeper.AsyncCallback.{Children2Callback, StringCallback, DataCallback, StatCallback}
import org.apache.zookeeper.KeeperException.Code
import java.util
import scala.util.Sorting
import util.Collections
import org.apache.zookeeper.ZooDefs.Perms._

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 13.09.12
 * Time: 12:24
 * To change this template use File | Settings | File Templates.
 */
class ServerRoyalExecutor(val hostPort: String, val myid: String, val myDbAddress: String) extends Watcher with Actor with RoyalExecutor {

  def process(e: WatchedEvent) {
    println("generic event : " + e)
    // process generic connection issues here
  }

  def act() {
    val continue = true
    val zk = new ZooKeeper(hostPort, 3000, this)

    val initMessage = new WatcherInitMessage(zk, this)

    masterElectActor.start() ! initMessage
    masterChangedWatcherActor.start() ! initMessage

    loopWhile(continue) {
      receive {
        case m: MasterDeletedMessage => {
          masterElectActor ! new MasterElectMessage
        }
        case MasterElectedAddressMessage(newMasterAddress) => {
          println("comparing new master : " + newMasterAddress + " with my myDbAddress : " + myDbAddress)
          if (myDbAddress == newMasterAddress) {
            println("new elected master is me so create ephemeral master node")
            zk.create(
              NodeConstants.MASTER_NODE,
              newMasterAddress.getBytes,
              List(new ACL(CREATE|DELETE|WRITE|READ,ZooDefs.Ids.ANYONE_ID_UNSAFE)),
              CreateMode.EPHEMERAL
            )
          } else { //we soon will have new master, just wait
            println("we soon will have new master, just wait")
            masterChangedWatcherActor ! new WatchAgainMessage
          }
        }
      }
    }
  }
}
