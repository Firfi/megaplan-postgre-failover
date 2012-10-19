package ru.megaplan.db.failover.server

import message._
import message.PromoteMessage
import message.PullMasterMessage
import message.Start
import ru.megaplan.db.failover.server.config.ApplicationConfig
import actors.Actor
import org.apache.zookeeper._
import org.apache.zookeeper.Watcher.Event.KeeperState
import com.codahale.logula.Logging
import com.twitter.zookeeper.{ZooKeeperClientConfiguration, ZooKeeperClient}
import java.lang.String
import ru.megaplan.db.failover.{DbConstants, NodeConstants}
import util.TimelineUtil
import scala.Some

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 19.09.12
 * Time: 20:42
 * To change this template use File | Settings | File Templates.
 */
class RoyalExecutor(val applicationConfig: ApplicationConfig) extends Actor with Logging {

  import applicationConfig._
  import CreateMode.EPHEMERAL

  private val royalExecutor = this

  private val deserialize = (b: Array[Byte]) => new String(b)

  private val servers = collection.mutable.Map.empty[String, String]

  private var currentMaster : String = "" // on first start we have empty master; after some initialisation it takes some value
                                          // and it isn't became empty anymore
  private def firstStart = currentMaster.isEmpty

  private var timelineUtil: Option[TimelineUtil] = None

  private val connectionWatcher = Option((keeper: ZooKeeperClient, state: KeeperState) => {
    var childCreatedOnce = false // protection from false starting agents with same configurations (server id particularly)
    try {
      import KeeperState._
      state match {
        case SyncConnected => {

          log.debug("syncConnected")
          if (timelineUtil.isEmpty) timelineUtil = Some(new TimelineUtil(keeper))
          val myNode = NodeConstants.SERVERS_ROOT+"/"+myId

          //register in cluster as ..../servers/{id}
          if (keeper.exists(myNode) != null) { // i found my node
            if (!childCreatedOnce) {
              log.error("CHILD EXISTS!")
              royalExecutor ! ShutdownMessage
            }
          } else {
            log.debug("creating child")
            keeper.create(myNode, dbAddress.getBytes, EPHEMERAL)
            childCreatedOnce = true
          }

          def electMaster {
            log.debug("elect master start")
            val candidateId = keeper.getChildren(NodeConstants.SERVERS_ROOT).min.toInt // election procedure
            log.debug("candidateId : %s", candidateId)
            if (candidateId == myId) { // candidateIsMe(candidateId)
              val candidate = {   // getCandidateAddress()
                def getCandidate: String = {
                  val candidate = servers.synchronized {
                    servers.get(candidateId.toString)
                  }
                  candidate.getOrElse({Thread.sleep(100); getCandidate}) // TODO remove this shit
                }
                getCandidate
              }

              def startLocationRefresh { timelineUtil.map(_.startRefreshWorker) }
              def createMasterNode { keeper.create(NodeConstants.MASTER_NODE, dbAddress.getBytes, EPHEMERAL) }
              def setCurrentMaster(candidate: String) {currentMaster = candidate}

              if (firstStart) {
                startLocationRefresh
                createMasterNode // assume that we started application on correct master (id 1 in this case)
                setCurrentMaster(candidate)
              } else {
                if (currentMaster != candidate) {
                  log.debug("promoting current to master : %s ?", candidate)
                  val maxExceed = 100500
                  if (timelineUtil.get.exceed(keeper, maxExceed)) {
                    log.warn("timeline gap exceeds %s bytes; do not promote", maxExceed)
                  } else {
                    configChangeQueue !? PromoteMessage(applicationConfig, Some(royalExecutor))
                    startLocationRefresh
                    setCurrentMaster(candidate)
                    createMasterNode
                  }
                }
              }
            }
          }

          def watchMaster {

            log.debug("starting watchMaster function")

            @volatile var pause = false

            keeper.watchNode(NodeConstants.PAUSE_SIG, (data, stat) => {
              data match {
                case None => {
                  log.debug("pause off")
                  pause = false
                }
                case Some(d) => {
                  log.debug("pause on")
                  pause = true
                }
              }
            })

            keeper.watchNode(NodeConstants.MASTER_NODE,(data, stat) => {
              log.debug("watch master node triggered")
              log.debug("pause is : " + pause)
              if (!pause) {
                data match {
                  case None => {
                    log.debug("master deleted or not exists")
                    electMaster
                  }
                  case Some(masterData) => {
                    val master = new String(masterData)
                    log.debug("found some master data : %s", master)
                    if (currentMaster != master) {
                      currentMaster = master
                      if (master != dbAddress) {
                        configChangeQueue ! PullMasterMessage(applicationConfig, master)
                      } else {
                        configChangeQueue !? PromoteMessage(applicationConfig) // this one aren't supposed to be called
                      }
                    }
                  }
                }
              } // maybe TODO else on pause OFF execute last action
            })
          }

          @volatile var shutdown = false

          keeper.watchNode(NodeConstants.SHUTDOWN_SIG, (data, stat) => {
            data match {
              case None => {
                log.debug("shutdown node isn't exist")
              }
              case Some(d) => {
                shutdown = true
                royalExecutor ! ShutdownMessage
              }
            }
          })

          log.debug("shutdown is : " + shutdown)
          log.debug("firstStart is : " + firstStart)

          if (!shutdown) {
            if (!firstStart) {
              log.debug("not first start")
              keeper.watchChildrenWithData(NodeConstants.SERVERS_ROOT, servers, deserialize, (key) => {
                log.debug("modified : " + key)
              })
              watchMaster
            } else {
              keeper.watchChildrenWithData(NodeConstants.SERVERS_ROOT, servers, deserialize, (key) => {
                log.debug("modified : " + key)
                servers.synchronized {
                  if (servers.size > 3 - 1) {
                    watchMaster
                  }
                }
              })
            }
          }
        }
        case Expired => {
          timelineUtil.map(_.stopRefreshWorker)
          log.warn("expired")
        }
      }
    } catch {
      case e: Exception => {
        log.warn(e,"ex")
        throw e
      }
    }
  })



  private def initZk : ZooKeeperClient = {
    try {
      new ZooKeeperClient(ZooKeeperClientConfiguration(
        servers = zooConnectString,
        sessionTimeout = 3000,
        connectTimeout = 10000,
        reconnectOnExpiration = true,
        basePath = "",
        sessionWatcher = connectionWatcher
      ))
    } catch {
      case e: Exception => {
        log.warn(e,"exception on connect")
        initZk
      }
    }

  }

  def act() {

    val zk = initZk

    var continue = true

    loopWhile(continue) {
      receive {
        case ShutdownMessage => {
          log.debug("received shutdown signal!")
          //timelineUtil.map(that => that.timelineRefreshWorker ! ShutdownMessage)
          zk.close()
          continue = false
        }
      }
    }
  }

}
