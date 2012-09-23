package ru.megaplan.db.failover.server.config.util

import ru.megaplan.db.failover.{DbConstants}
import java.util.Properties
import scala.Some
import ru.megaplan.db.failover.server.config.ApplicationConfig
import com.codahale.logula.Logging
import java.io.{FileInputStream, FileOutputStream, File}
import java.nio.channels.Channels

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.09.12
 * Time: 15:33
 * To change this template use File | Settings | File Templates.
 */
class ConfigUtil(applicationConfig: ApplicationConfig) extends Logging {

  val dbPath = applicationConfig.dbPath
  val restartDbScript = applicationConfig.restartScript

  def setRecoveryMode {
    val confFile = new File(dbPath+"/"+DbConstants.RECOVERY_CONF)
    val doneFile = new File(dbPath+"/"+DbConstants.RECOVERY_DONE)
    val dbDir = new File(dbPath)
    if (!dbDir.canWrite) {
      throw new RuntimeException("can't write in directory : " + dbPath)
    } else {
      if (!confFile.exists() && !doneFile.exists()) {
        confFile.createNewFile()
        val source = Channels.newChannel(this.getClass.getClassLoader.getResourceAsStream("recovery.conf"))
        val destination = new FileOutputStream(confFile)
        destination.getChannel.transferFrom(source, 0, Long.MaxValue)
        source.close()
        destination.close()
      } else {
        if (!confFile.exists()) {
          val source = new FileInputStream(doneFile).getChannel
          confFile.createNewFile()
          val destination = new FileOutputStream(confFile).getChannel
          destination.transferFrom(source, 0, Long.MaxValue)
          source.close()
          destination.close()
        }
      }
    }
  }

  private def getMasterValueFromString(confString: String) = {
    confString.split(" ").filter(s => s.startsWith("host")||s.startsWith("port")).
      sortWith((h,p) => h=="host").map(fs => fs.split("=")(1)).mkString(":")
  }

  def getCurrentMasterValue: Option[String] = {
    val option = getMasterAndTrigger
    option match {
      case None => Option.empty
      case Some((masterPath: String, trigger)) => Option(masterPath)
    }
  }

  def getMasterAndTrigger: Option[(String, String)] = {
    val recoveryConfFile = new File(dbPath+"/"+DbConstants.RECOVERY_CONF)
    if (recoveryConfFile.exists()) {
      val properties = getProperties(recoveryConfFile)
      Option(
        getMasterValueFromString(getStrippedValue(properties, "primary_conninfo")),
        getStrippedValue(properties, "trigger_file")
      )
    } else {
      Option.empty
    }
  }

  def setCurrentMasterValue(hostPort: String) {
    val Array(host, port) = hostPort.split(":")
    val recoveryConfFile = new File(dbPath+"/"+DbConstants.RECOVERY_CONF)
    if (!recoveryConfFile.exists()) {
      setRecoveryMode
    }
    val properties = getProperties(recoveryConfFile)

    val newValue = getStrippedValue(properties, "primary_conninfo").
      split(" ").map(part => {
        if (part.startsWith("host")) {
          "host="+host
        } else if (part.startsWith(port)) {
          "port="+port
        } else {
          part
        }
      }).mkString("'"," ","'")

    properties.setProperty("primary_conninfo", newValue)
    saveProperties(properties)
  }

  def restartDb {
    val process = java.lang.Runtime.getRuntime.exec(restartDbScript + " " + dbPath)
    val res = process.waitFor()
    log.info("restartScript : " + restartDbScript + " executed with result code : " + res)
  }

  private def getStrippedValue(props: Properties, key: String) = {
    var s = props.get(key).toString
    if (s.charAt(0)=='\'') s = s.substring(1)
    if (s.charAt(s.length-1)=='\'') s = s.substring(0,s.length-1)
    s
  }

  //TODO: it is currently not thread-safe
  private def getProperties(recoveryConfFile: File) = {
    val properties: Properties = new Properties()
    properties.load(new FileInputStream(recoveryConfFile))
    properties
  }

  private def saveProperties(properties: Properties) {
    properties.store(new FileOutputStream(getRecoveryConfFile),null)
  }

  private def getRecoveryConfFile = new File(dbPath+"/"+DbConstants.RECOVERY_CONF)

}
