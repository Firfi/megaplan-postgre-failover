package ru.megaplan.db.failover2.server

import message.{IAmMasterNowMessage, PullMasterMessage, PromoteMessage}
import ru.megaplan.db.failover.util.LogHelper
import actors.Actor
import ru.megaplan.db.failover.server.config.ApplicationConfig
import ru.megaplan.db.failover.server.config.util.ConfigUtil

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 9/22/12
 * Time: 7:08 PM
 * To change this template use File | Settings | File Templates.
 */
object configChangeQueue extends Actor with LogHelper {
  def act() {
    loop {
      receive {
        case PromoteMessage(caller: Actor) => {

          caller ! IAmMasterNowMessage
        }
        case PullMasterMessage(applicationConfig: ApplicationConfig, master: String) => {
          val masterIp = master.split(':')(0)

        }
      }
    }
  }
}
