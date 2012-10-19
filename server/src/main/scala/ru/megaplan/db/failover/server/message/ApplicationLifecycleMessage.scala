package ru.megaplan.db.failover.server.message

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 05.10.12
 * Time: 13:52
 * To change this template use File | Settings | File Templates.
 */
abstract class ApplicationLifecycleMessage
case object ShutdownMessage extends ApplicationLifecycleMessage
