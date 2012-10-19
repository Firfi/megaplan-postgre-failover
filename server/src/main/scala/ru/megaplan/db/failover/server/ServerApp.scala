package ru.megaplan.db.failover.server

import com.codahale.logula.Logging
import config.ApplicationConfig
import java.io.File
import com.typesafe.config.ConfigFactory
import message.ShutdownMessage
import org.apache.log4j.Level
import actors.Actor

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 04.10.12
 * Time: 11:58
 * To change this template use File | Settings | File Templates.
 */
object ServerApp extends App with Logging {
  override def main(args: Array[String]) {

    if (args.isEmpty) {
      log.error("usage : java -jar jarname.jar application.conf")
      System.exit(2)
    }
    val confFile = new File(args(0))
    if (!confFile.exists()) {
      log.error("error: confFile : " + args(0) + " not exists")
      System.exit(2)
    }

    val applicationConfig = new ApplicationConfig(ConfigFactory.parseFile(new File(args(0))))

    setLogging(applicationConfig.logFile)

    val royalExecutor = new RoyalExecutor(applicationConfig)
    demonize(royalExecutor)
    royalExecutor.start()
  }

  def setLogging(logfile: String) {
    Logging.configure {
      log => {

        log.level = Level.INFO

        log.loggers("ru.megaplan") = Level.DEBUG

        log.console.enabled = true
        log.console.threshold = Level.DEBUG

        log.file.enabled = true
        log.file.filename = logfile
        log.file.maxSize = 10 * 1024
        log.file.retainedFiles = 5
        log.file.threshold = Level.DEBUG

      }
    }
  }

  def demonize(mainActor : Actor) {
    //System.in.close()
    //System.out.close()
    sys addShutdownHook {
      mainActor ! ShutdownMessage
    }
  }

}

