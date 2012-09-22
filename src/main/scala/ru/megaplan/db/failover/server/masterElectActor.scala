package ru.megaplan.db.failover.server

import actors.Actor
import message.{MasterElectedAddressMessage, MasterElectedMessage, MasterElectMessage, WatcherInitMessage}
import org.apache.zookeeper.{CreateMode, ZooDefs, ZooKeeper}
import ru.megaplan.db.failover.NodeConstants
import org.apache.zookeeper.AsyncCallback.{DataCallback, Children2Callback}
import java.util
import org.apache.zookeeper.data.{ACL, Stat}
import org.apache.zookeeper.KeeperException.{NodeExistsException, Code}
import org.apache.zookeeper.KeeperException.Code._
import util.Collections
import com.codahale.logula.Logging

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 14.09.12
 * Time: 17:06
 * To change this template use File | Settings | File Templates.
 */
object masterElectActor extends Actor with Logging {

  val masterElectDataCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case OK => {
          val masterAddress = new String(data)
          log.debug("arrived new master address data : " + masterAddress)
          masterElectActor ! new MasterElectedAddressMessage(masterAddress)
        }
        case NONODE => { // candidate master died before we read its address, so elect again
          masterElectActor ! new MasterElectMessage
        }
      }
    }
  }

  val masterElectCallback = new Children2Callback {

    def electNewMaster(candidates: util.List[String]) = {
      Collections.sort(candidates) // 1, 2, 3
      candidates.get(0)
    }

    def processResult(rc: Int, path: String, ctx: Any, children: util.List[String], stat: Stat) {
      val code = Code.get(rc)
      code match {
        case OK => {
          val newMaster = electNewMaster(children)
          log.info("elected new master : " + newMaster)
          masterElectActor ! new MasterElectedMessage(newMaster)
        }
        case _ => {}
      }
    }
  }

  def act() {
    var zk: ZooKeeper = null
    var daddy: ServerRoyalExecutor = null
    loop {
      receive {
        case WatcherInitMessage(zooKeeper, serverRoyalExecutor) => {
          zk = zooKeeper
          daddy = serverRoyalExecutor
          try {
            zk.create(  // here masterChangedWatcherActor has to be in active watch // TODO: if not add master created message, detka
              NodeConstants.SERVERS_ROOT+"/"+daddy.applicationConfig.myId,
              daddy.applicationConfig.dbAddress.toCharArray.map(_.toByte),
              ZooDefs.Ids.READ_ACL_UNSAFE,
              CreateMode.EPHEMERAL
            )
            reply(true)
          } catch {
            case e: NodeExistsException => {
              reply(false)
            }
            case e: Exception => {
              reply(false)
              log.warn("exteprion in child node creating", e)
            }
          }

        }
        case m: MasterElectMessage => {
          zk.getChildren(NodeConstants.SERVERS_ROOT, false, masterElectCallback, null)
        }
        case MasterElectedMessage(id) => {
          zk.getData(NodeConstants.SERVERS_ROOT+"/"+id, false, masterElectDataCallback, null)
        }
        case m: MasterElectedAddressMessage => {
          daddy ! m
        }
      }
    }
  }
}
