package ru.megaplan.db.failover.server

import actors.Actor
import actors.Actor._
import ru.megaplan.db.failover.message.{ClientInitMasterMessage, ApplicationExitMessage}
import ru.megaplan.db.failover.client.ClientRoyalExecutor

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 13.09.12
 * Time: 12:19
 * To change this template use File | Settings | File Templates.
 */
object ServerApp extends App {

  override def main(args: Array[String]) {
    if (args.length < 3) {
      System.err
        .println("USAGE: hostPort myid myDbAddress")
      System.exit(2)
    }
    val hostPort = args(0)
    val myId = args(1)
    val myDbAddress = args(2)

    val royalExecutor = new ServerRoyalExecutor(hostPort, myId, myDbAddress)
    royalExecutor.start()

  }

}
