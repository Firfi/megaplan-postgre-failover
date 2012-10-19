package ru.megaplan.db.failover.client

import com.codahale.logula.Logging


/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 11.09.12
 * Time: 16:26
 * To change this template use File | Settings | File Templates.
 */

object ClientApp extends App with Logging {

  override def main(args: Array[String]) {

    println("starting main")
    if (args.length < 3) {
      System.err
        .println("USAGE: hostPort shellScript(masterAddress: String) iniConfig")
      System.exit(2)
    }
    val hostPort = args(0)
    val shell = args(1)
    val ini = args(2)
    log.warn(hostPort + " on initialization")

    val royalExecutor = new ClientRoyalExecutor(hostPort, shell, ini)
    royalExecutor.start()

  }




}
