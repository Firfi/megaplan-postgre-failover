package ru.megaplan.db.failover2.server.message

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 17:53
 * To change this template use File | Settings | File Templates.
 */
abstract class FailoverActorMessage
case object FailoverActorTerminateMessage extends FailoverActorMessage
case object MasterCreatedMessage extends FailoverActorMessage
case object MasterDeletedMessage extends FailoverActorMessage
case class MasterElectedMessage(master: String) extends FailoverActorMessage
case object IAmMasterNowMessage extends FailoverActorMessage
case object NoMasterMessage extends FailoverActorMessage
case object MasterExistsMessage extends FailoverActorMessage
