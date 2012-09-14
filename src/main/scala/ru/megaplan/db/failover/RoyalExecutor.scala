package ru.megaplan.db.failover

import org.apache.zookeeper.{Watcher, ZooKeeper}
import actors.Actor

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 13.09.12
 * Time: 12:39
 * To change this template use File | Settings | File Templates.
 */
trait RoyalExecutor extends Watcher with Actor {
  private val DEFAULT_TIMEOUT = 3000
  protected var MAIN_TIMEOUT = DEFAULT_TIMEOUT
  val royalExecutor = this
}
