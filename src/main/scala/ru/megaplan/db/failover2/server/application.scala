package ru.megaplan.db.failover2.server

import ru.megaplan.db.failover.server.config.ApplicationConfig
import java.io.File
import com.typesafe.config.ConfigFactory
import com.codahale.logula.Logging
import org.apache.log4j.Level

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 19.09.12
 * Time: 20:41
 * To change this template use File | Settings | File Templates.
 */
object application extends App with Logging {
  override def main(args: Array[String]) {
    val confFile = new File(args(0))
    if (!confFile.exists()) {
      println("error: confFile : " + args(0) + " not exists")
      System.exit(2)
    }
    println(ConfigFactory.parseFile(new File(args(0))))
    val applicationConfig = new ApplicationConfig(ConfigFactory.parseFile(new File(args(0))))
    val royalExecutor = new RoyalExecutor(applicationConfig)
    royalExecutor.start()
  }

  Logging.configure { log =>
    log.level = Level.INFO
    log.loggers("ru.megaplan") = Level.DEBUG

    log.console.enabled = true
    log.console.threshold = Level.WARN

    //log.file.enabled = true
    //log.file.filename = "/var/log/myapp/myapp.log"
    //log.file.maxSize = 10 * 1024 // KB
    //log.file.retainedFiles = 5 // keep five old logs around

    // syslog integration is always via a network socket
    //log.syslog.enabled = true
    //log.syslog.host = "syslog-001.internal.example.com"
    //log.syslog.facility = "local3"
  }

}
