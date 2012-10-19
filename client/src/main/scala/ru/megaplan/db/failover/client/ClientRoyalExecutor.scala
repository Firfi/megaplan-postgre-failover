package ru.megaplan.db.failover.client

import org.apache.zookeeper.{ZooKeeper, WatchedEvent, Watcher}
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.AsyncCallback.{VoidCallback, StatCallback, DataCallback}
import ru.megaplan.db.failover.NodeConstants
import actors.Actor
import com.codahale.logula.Logging
import io.Source
import java.io.{InputStream, InputStreamReader, FileOutputStream}
import scala.Option
import com.twitter.zookeeper.{ZooKeeperClientConfiguration, ZooKeeperClient}

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 12.09.12
 * Time: 12:19
 * To change this template use File | Settings | File Templates.
 */
class ClientRoyalExecutor(val servers: String, val shell: String, val ini: String) extends Actor with Logging {

  def getCurrentMaster: (Option[String], Option[String]) = {
    Source.fromFile(ini).getLines().find(line => line.contains("host=")).map(line => {
      val hostPattern = ".*host=([^ ]+).*".r; val hostPattern(host) = line
      val portPattern = ".*port=([^ ]+).*".r; val portPattern(port) = line
      (Option(host), Option(port))
    }).get
    //.map(line => {
      //if (line.contains("host=")) line.replaceFirst("host=[^ ]+","host="+addressAndPort.split(':')(0)) else line
    //}).mkString("\n").getBytes
  }



  def act() {

    var currentMaster = {
      val cm = getCurrentMaster
      cm._1.get+{
        val r = cm._2.getOrElse("")
        if (r.isEmpty) r else ":"+r
      }
    }

    def initZk : ZooKeeperClient = {
      new ZooKeeperClient(ZooKeeperClientConfiguration(servers, 3000, 15000, true, "", Option((keeper, keeperState) => {
        if (keeperState == KeeperState.SyncConnected) {
          keeper.watchNode(NodeConstants.MASTER_NODE, (data, stat) => {
            data match {
              case Some(bytes) => {
                val master = new String(bytes)
                if (master != currentMaster) {
                  log.debug("master changed to : " + master)
                  currentMaster = master
                  updateMaster(master)
                  restartPg
                }
              }
              case None => {log.debug("master deleted!")} // master deleted, just wait
            }
          })
        }
      })))
    }

    initZk

    loop {

    }
  }

  def updateMaster(master: String) {

    val newContent = Source.fromFile(ini).getLines().map(line => {
      if (line.contains("host=")) {
        line.split(" ").map(w => {
          if (w.contains("host=")) {
            "host="+master.split(":")(0)
          } else if (w.contains("port=")) {
            "port="+master.split(":")(1)
          } else {
            w
          }
        }).mkString(" ")
      } else line
    }).mkString("\n").getBytes

    val fos = new FileOutputStream(ini)
    log.warn(new String(newContent))
    fos write newContent
    fos.close()

  }

  def restartPg {
    log.debug("restarting pgbouncer")
    val cmd = shell + " " + ini + " > " + shell+".restarted.log"
    val run = Runtime.getRuntime.exec(cmd)
    def runLog(stream: InputStream) {
      Actor.actor {
        val irr = new InputStreamReader(stream)
        var data = irr.read()
        val message = new StringBuilder()
        while (data != -1) {
          val theChar = data.toChar
          message.append(theChar)
          if ('\n' == theChar) {
            log.warn(message.toString())
            message.clear()
          }
          data = irr.read()
        }
        irr.close()
      }
    }
    runLog(run.getErrorStream)
    runLog(run.getInputStream)
    log.debug("restart pgbouncer done, command: " + cmd)
  }

}
