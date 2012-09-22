package ru.megaplan.db.failover2.server.message

import ru.megaplan.db.failover.server.config.ApplicationConfig
import actors.Actor

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 9/22/12
 * Time: 7:09 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class ConfigChangeMessage
case class PromoteMessage(caller: Actor)
case class PullMasterMessage(applicationConfig: ApplicationConfig, master: String)
