package ru.megaplan.db.failover.server

import message.{PauseMessage, PullMasterMessage, PromoteMessage}
import actors.{DaemonActor, Actor}
import ru.megaplan.db.failover.server.config.ApplicationConfig
import com.codahale.logula.Logging
import java.io.{InputStream, OutputStream}
import java.nio.channels.{Channels, Channel}
import java.nio.ByteBuffer

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 9/22/12
 * Time: 7:08 PM
 * To change this template use File | Settings | File Templates.
 */
object configChangeQueue extends DaemonActor with Logging {
  start()
  def act() {

    var pause = false

    def receiveConfigChanges {
      loopWhile(!pause) {
        Actor.receive {
          case PromoteMessage(applicationConfig: ApplicationConfig, caller: Option[Actor]) => {
            log.debug("promoting to master with script : " + applicationConfig.promoteScript)
            val res = Runtime.getRuntime.exec(applicationConfig.promoteScript)
            res.waitFor()
            reply("ok")
          }
          case PullMasterMessage(applicationConfig: ApplicationConfig, master: String) => {
            val masterIp = master.split(':')(0)
            log.debug("starting pull master script : " + applicationConfig.masterPullScript + " " + masterIp)
            import scala.sys.process._
            val res = Seq(applicationConfig.masterPullScript, masterIp).!!
            log.debug("pull master script done with result : " + res)
          }
          case PauseMessage(true) => {
            pause = true
          }
        }
      }

      waitResume

    }

    def waitResume {

      var waitResume = true

      loopWhile(waitResume) {
        Actor.receive {
          case PauseMessage(false) => {
            pause = false
            waitResume = false
          }
        }
      }

      receiveConfigChanges

    }

    receiveConfigChanges

  }

}
