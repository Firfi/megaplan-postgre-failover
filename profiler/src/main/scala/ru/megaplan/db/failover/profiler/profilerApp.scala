package ru.megaplan.db.failover.profiler

import com.codahale.logula.Logging
import java.sql.{ResultSet, DriverManager, Connection}
import actors.Actor._
import actors.Actor
import collection.parallel.mutable
import collection.mutable.ListBuffer

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 26.09.12
 * Time: 13:34
 * To change this template u
 * se File | Settings | File Templates.
 */
object profilerApp extends App with Logging {






  override def main(args: Array[String]) {

    val maxIter = 150
    val maxInnerIter = 20

    def runBench(id: String, lostStatementCollector: Actor, currentIter: Int = 1) {
      var nextIter = currentIter
      val con = getConnection
      try {
        log.warn("obtained connection in : " + id)
        val selectStatement = con.prepareStatement("select * from test")
        val insertStatement = con.prepareStatement("insert into test values(?)")
        for (i <- currentIter to maxInnerIter) {
          Thread.sleep(10)
          log.warn("executing select statement")
          selectStatement.execute()
          log.warn(id + " executing insert statement")
          insertStatement.setString(1, (id.toInt*maxInnerIter+i).toString)
          insertStatement.execute()
          nextIter = nextIter + 1
        }
        con.close()
        if (id.startsWith(maxIter.toString))
          lostStatementCollector ! "printLostCount"
      } catch {
        case e: Exception => {
          log.warn(e, id + "exception in bench")
          lostStatementCollector ! "lost"
          con.close()
          runBench(id, lostStatementCollector, nextIter)
        }
      }

    }

    val lostStatementCollector = actor {
      def countLostInserts: String = {
        def getIds(set: ResultSet): List[Int] = {
          var result = List[Int]()
          while (set.next()) {
            val value = set.getString(1)
            result = value.toInt :: result
          }
          set.close()
          result.sortWith(_<_)
        }

        def getLost(con: Connection): (Int, Int, List[Int]) = {
          if (con == null) (-1, -1, List.empty)
          else {
            val selectStatement = con.prepareStatement("select * from test")
            val ids = getIds(selectStatement.executeQuery())
            con.close()
            log.warn(ids.mkString(" "))
            ids.zip(ids.tail).foldLeft((0, 0, List.empty[Int]))((acc: (Int, Int, List[Int]), pair: (Int, Int)) => {
              val gap: List[Int] = (pair._1 until pair._2).toList
              if (gap.length == 0) {
                log.warn("gap length is null and start : " + pair._1 + " and end : " + pair._2)
              }
              val gp = {if (gap.length==0) gap else gap.tail}
              (acc._1 + gp.length, {if (gap.length==0) acc._2 + 1 else acc._2}, gp ::: acc._3)
            })
          }
        }
        Thread.sleep(2000)
        var c: Connection = null
        actor { c = getConnection }
        var c1: Connection = null
        actor { c1 = getConnection("10.10.0.101", "5432", "test") }
        var c2: Connection = null
        actor { c2 = getConnection("10.10.0.102", "5432", "test") }
        var c3: Connection = null
        actor { c3 = getConnection("10.10.0.103", "5432", "test") }
        Thread.sleep(2000)
        val lost0 = getLost(c)
        val lost1 = getLost(c1)
        val lost2 = getLost(c2)
        val lost3 = getLost(c3)
        "0: " + lost0._1 + "/" + lost0._2 +
          " 1:" + lost1._1 + "/" + lost1._2 +
          " 2:" + lost2._1 + "/" + lost2._2 +
          " 3:" + lost3._1 + "/" + lost3._2 + '\n' +
        lost0._3.mkString("lost0: ",", ","") + '\n' +
          lost1._3.mkString("lost1: ",", ","") + '\n' +
          lost2._3.mkString("lost2: ",", ","") + '\n' +
          lost3._3.mkString("lost3: ",", ","")
      }
      var lostCount: Int = 0
      var continue = true
      loopWhile(continue) {
        receive {
          case "lost" => {
            lostCount = lostCount + 1
            log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!! exception count : " + lostCount)
          }
          case "printLostCount" => {
            log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!! result exception count : " + lostCount)
            for (i <- 0 to 20) {
              log.warn(countLostInserts)
              Thread.sleep(3000)
            }
            continue = false
          }
        }
      }
    }

    //clear old date
    val con = getConnection
    val deleteStatement = con.prepareStatement("delete from test")
    deleteStatement.execute()

    for (i <- 0 to maxIter) {
      log.warn("tick " + i)
      actor {
        runBench(i.toString, lostStatementCollector)
      }
      Thread.sleep(100)
    }
  }



  def getConnection(ip: String, port: String, db: String): Connection = {
    Option(DriverManager.getConnection("jdbc:postgresql://"+ip+":"+port+"/"+db,"postgres",null)).
      getOrElse({log.warn("no connection");System.exit(2);null})
  }

  def getConnection: Connection = {
    getConnection("127.0.0.1", 6543.toString, "postgres")
  }

}
