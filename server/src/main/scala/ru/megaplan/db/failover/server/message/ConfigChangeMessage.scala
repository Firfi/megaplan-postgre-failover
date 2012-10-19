package ru.megaplan.db.failover.server.message

import actors.Actor
import ru.megaplan.db.failover.server.config.ApplicationConfig

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 9/22/12
 * Time: 7:09 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class ConfigChangeMessage
case class PromoteMessage(applicationConfig: ApplicationConfig, caller: Option[Actor] = None)
case class PullMasterMessage(applicationConfig: ApplicationConfig, master: String)
case class PauseMessage(pause: Boolean)
