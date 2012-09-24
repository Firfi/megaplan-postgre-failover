package ru.megaplan.db.failover2.server

import ru.megaplan.db.failover.util.LogHelper
import ru.megaplan.db.failover.server.config.ApplicationConfig
import ru.megaplan.db.failover.server.ServerRoyalExecutor
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.DomDriver
import java.io.File

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 19.09.12
 * Time: 20:41
 * To change this template use File | Settings | File Templates.
 */
class Application extends App with LogHelper {
  override def main(args: Array[String]) {

    val applicationConfig: ApplicationConfig = if (args.isEmpty) readConfig("") else readConfig(args(0))
    val royalExecutor = new RoyalExecutor(applicationConfig)
    royalExecutor.start()

  }

  def readConfig(configPath: String): ApplicationConfig = {
    def isPath(path: String): Boolean = {
      try {
        val p = new java.io.File(path)
        if (!p.exists()) {
          log.error("given config parh : " + configPath + " not exist")
          System.exit(0)
        }
        if (!p.canRead || !p.canWrite) {
          log.error("not enough permissions for read or write config file : " + configPath)
          System.exit(0)
        }

      } catch {
        case e: Exception => {
          log.error("exception in reading config path : " + configPath,e)
          false
        }
      }
      true
    }
    val xStream = new XStream(new DomDriver())
    xStream.processAnnotations(classOf[ApplicationConfig])
    val result = {
      if (!configPath.isEmpty && isPath(configPath)) {
        xStream.fromXML(new File(configPath))
      }
      else xStream.fromXML(this.getClass.getClassLoader.getResource("config.xml"))
    }
    log.warn(result)
    result match {
      case appConfig: ApplicationConfig => appConfig
      case _ => throw new ClassCastException
    }
  }
}
