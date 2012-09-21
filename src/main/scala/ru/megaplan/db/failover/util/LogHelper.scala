package ru.megaplan.db.failover.util

import org.apache.log4j.Logger

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.09.12
 * Time: 13:29
 * To change this template use File | Settings | File Templates.
 */
trait LogHelper {
  val loggerName = this.getClass.getName
  lazy val log = Logger.getLogger(loggerName)
}