package ru.megaplan.db.failover.server.util

import com.twitter.zookeeper.ZooKeeperClient
import ru.megaplan.db.failover.NodeConstants.MASTER_TIMELINE
import org.apache.zookeeper.{CreateMode, KeeperException}
import com.codahale.logula.Logging
import ru.megaplan.db.failover.server.message._
import java.sql._
import actors.DaemonActor
import scala.Array
import ru.megaplan.db.failover.server.message.Stop
import ru.megaplan.db.failover.server.message.Start


/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 09.10.12
 * Time: 15:36
 * To change this template use File | Settings | File Templates.
 */

import java.lang.Long

object TimeLineUtil {
  val RECEIVE_LOCATION_QUERY = "select pg_last_xlog_receive_location();"
  val MASTER_LOCATION_QUERY = "select pg_current_xlog_location();"
}

class TimelineUtil(zk: ZooKeeperClient) extends Logging {

  def hex2long (hex: String): Long = Long.parseLong(hex, 16)

  def toBytes(timeline: String): Long = {
    val Array(id, offset) = timeline.split("/")
    0xffffffff.toLong*hex2long(id) + hex2long(offset)
  }

  def isValid(timeline: String) = {
    if (timeline == null || timeline.isEmpty) false
    else {
      try {
        val Array(id, offset) = timeline.split("/")
        hex2long(id)
        hex2long(offset)
        true
      } catch {
        case e: MatchError => {
          false
        }
        case e: Exception => {
          false
        }
      }
    }
  }

  def exceed(zk: ZooKeeperClient, maxExceed: Long): Boolean = {
    try {
      val masterTimeline = new String(zk.get(MASTER_TIMELINE))
      val standbyTimeline = getStandbyTimeline

      log.warn("---")
      log.warn("masterTimeline : " + masterTimeline)
      log.warn("standbyTimeline : " + standbyTimeline)

      if (!isValid(masterTimeline) || !isValid(standbyTimeline)) false
      else {
        val delta = toBytes(masterTimeline) - toBytes(standbyTimeline)
        log.debug("delta : " + delta)
        if (delta > maxExceed) true
        else false
      }

    } catch {
      case e: KeeperException.NoNodeException => {
        log.error("cant'find master timeline in zk cluster by address : %s", MASTER_TIMELINE)
        zk.create(MASTER_TIMELINE, "".toCharArray.map(_.toByte), CreateMode.PERSISTENT)
        exceed(zk, maxExceed)
      }
      case e: SQLException => {
        log.error(e, "some sql exception in exceed function")
        Thread.sleep(3000)
        exceed(zk, maxExceed)
      }
    }

  }

  def getConnection: Connection = {
    Option(DriverManager.getConnection("jdbc:postgresql:postgres")).
      getOrElse({log.warn("no connection");System.exit(2);null})
  }

  def getStandbyTimeline: String = {
    val con = getConnection
    val rs = con.prepareStatement(TimeLineUtil.RECEIVE_LOCATION_QUERY).executeQuery()
    try {
      if (rs.next()) {
        rs.getString(1)
      } else {
        ""
      }
    } finally {
      rs.close()
      con.close()
    }
  }

  import actors.Actor._

  def startRefreshWorker {
    timelineRefreshWorker ! Start
  }

  def stopRefreshWorker {
    timelineRefreshWorker ! Stop
  }

  val timelineRefreshWorker = actor {

    log.info("starting timeline refresh worker")

    var con: Connection = null

    def refreshTimelineData {
      val ps = {
        var tries = 0
        val maxTries = 5
        def pr: PreparedStatement = {
          try {
              tries = tries + 1
              con.prepareStatement(TimeLineUtil.MASTER_LOCATION_QUERY)
          } catch {
            case e: Exception => {
              if (tries >= maxTries) {
                throw e
              } else {
                log.warn(e, "exception on preparing statement, trying refresh")
                Thread.sleep(3000)
                con = getConnection
                pr
              }
            }
          }
        }
        pr
      }

      log.debug("getting timeline")

      val timeline = {
        var rs: ResultSet = null
        try {
          rs = ps.executeQuery()
          if (!rs.next()) ""
          else rs.getString(1)
        } finally {
          rs.close
        }
      }

      log.debug("end getting timeline : " + timeline)

      if (!timeline.isEmpty) {
        var tries = 0
        val maxTries = 5
        def setTimeline {
          if (tries < maxTries) {
            tries = tries + 1
            zk.set(MASTER_TIMELINE, timeline.getBytes)
          }
        }
        try {
          setTimeline
        } catch {
          case e: KeeperException => {
            log.warn(e, "keeper exception on setting timeline")
            Thread.sleep(1000)
            setTimeline
          }
        }
      } else {
        log.error("obtained empty result from pg_current_xlog_location()")
      }
    }

    var continue = true

    loopWhile(continue) {

      var refresh = false

      receive {
        case Start => {
          con = getConnection
          log.debug("timeline refresh worker starting with connection : " + con)
          refresh = true
        }
        case ShutdownMessage => {
          continue = false
        }
        case _ => {}
      }

      loopWhile(refresh) {

        try {
          refreshTimelineData
        } catch {
          case e: Exception => {
            log.warn(e, "exception on obtaining timeline data")
          }
        }


        receiveWithin(10) {
          case Stop => {
            log.debug("timeline refresh worker received stop message")
            refresh = false
          }
          case ShutdownMessage => {
            refresh = false
            continue = false
            con.close()
          }
          case _ => {}
        }
      }
    }
  }

}
