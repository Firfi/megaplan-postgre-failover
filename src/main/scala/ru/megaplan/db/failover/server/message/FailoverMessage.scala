package ru.megaplan.db.failover.server.message

import org.apache.zookeeper.ZooKeeper
import ru.megaplan.db.failover.server.ServerRoyalExecutor

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 14.09.12
 * Time: 13:03
 * To change this template use File | Settings | File Templates.
 */
abstract class FailoverMessage
abstract class MasterStatusMessage extends FailoverMessage

class RoyalExecutorInitMessage extends FailoverMessage
class WaitForStartMessage extends FailoverMessage
class InitMasterElectActorMessage extends FailoverMessage
class SessionExpiredMessage extends FailoverMessage

case class WatcherInitMessage(zk: ZooKeeper, serverRoyalExecutor: ServerRoyalExecutor) extends FailoverMessage

class WatchAgainMessage extends FailoverMessage
class StartWatchMessage extends FailoverMessage

class MasterExistsMessage extends MasterStatusMessage
class MasterCreatedMessage extends MasterStatusMessage
class MasterChangedMessage extends MasterStatusMessage
class MasterDeletedMessage extends MasterStatusMessage

class MasterElectMessage extends FailoverMessage
case class MasterElectedMessage(masterId: String) extends FailoverMessage
case class MasterElectedAddressMessage(masterAddress: String) extends FailoverMessage


