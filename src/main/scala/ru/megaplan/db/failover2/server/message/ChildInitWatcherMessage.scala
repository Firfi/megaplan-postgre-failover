package ru.megaplan.db.failover2.server.message

import org.apache.zookeeper.ZooKeeper

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 20.09.12
 * Time: 18:59
 * To change this template use File | Settings | File Templates.
 */
abstract class ChildInitWatcherMessage
case class MyChildNodeDisappear(zk: ZooKeeper)
