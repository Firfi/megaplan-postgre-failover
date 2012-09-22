package ru.megaplan.db.failover2.server

import message.{IAmMasterNowMessage, PullMasterMessage, PromoteMessage}
import actors.Actor
import ru.megaplan.db.failover.server.config.ApplicationConfig
import com.codahale.logula.Logging

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 9/22/12
 * Time: 7:08 PM
 * To change this template use File | Settings | File Templates.
 */
object configChangeQueue extends Actor with Logging {
  def act() {
    loop {
      receive {
        case PromoteMessage(applicationConfig: ApplicationConfig, caller: Actor) => {
          Runtime.getRuntime.exec(applicationConfig.promoteScript)
          caller ! IAmMasterNowMessage
        }
        case PullMasterMessage(applicationConfig: ApplicationConfig, master: String) => {
          val masterIp = master.split(':')(0)
          Runtime.getRuntime.exec(applicationConfig.masterPullScript + " " + masterIp).waitFor
        }
      }
    }
  }
}
