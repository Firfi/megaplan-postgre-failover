package ru.megaplan.db.failover2.server.message

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 17:42
 * To change this template use File | Settings | File Templates.
 */
abstract class ApplicationWorkflowMessage
case object ReinitApplicationMessage extends ApplicationWorkflowMessage
case object StopApplicatiomMessage extends ApplicationWorkflowMessage

