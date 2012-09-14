package ru.megaplan.db.failover.client

import actors.Actor.{loopWhile, react}
import actors.Actor
import ru.megaplan.db.failover.message.{ClientInitMasterMessage, ApplicationExitMessage}


/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 11.09.12
 * Time: 16:26
 * To change this template use File | Settings | File Templates.
 */

object ClientApp extends App {

  override def main(args: Array[String]) {
    if (args.length < 2) {
      System.err
        .println("USAGE: hostPort shellScript(masterAddress: String)")
      System.exit(2)
    }
    val hostPort = args(0)
    val shell = args(1)

    val royalExecutor = new ClientRoyalExecutor(hostPort, shell)
    royalExecutor.start()

  }


}
