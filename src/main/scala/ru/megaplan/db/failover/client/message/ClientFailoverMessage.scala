package ru.megaplan.db.failover.message

import actors.Actor

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 12.09.12
 * Time: 18:09
 * To change this template use File | Settings | File Templates.
 */
abstract class ClientFailoverMessage
case class ApplicationExitMessage(caller: Any)
case class ClientInitMasterMessage(caller: Any)