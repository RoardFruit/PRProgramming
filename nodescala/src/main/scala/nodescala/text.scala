package nodescala

import scala.language.postfixOps
import scala.util.{Try, Success, Failure}
import scala.collection._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.async.Async.{async, await}
import org.scalatest._
import NodeScala._
import org.scalatest.junit.JUnitRunner

/**
 * Created by Mr.gong on 2016/5/5.
 */
object text {
def main(args: Array[String]) {

 val working = Future.run() { ct =>
    Future {
      while (ct.nonCancelled) {
        println("working"+ Thread.currentThread().getName)
      }
    println("done"+ Thread.currentThread().getName)
    }
  }
  //println("OK" + Thread.currentThread().getName)
  Future.delay(3 seconds) onSuccess {
    case _ =>working.unsubscribe()
      println("OK" + Thread.currentThread().getName)
  }
  Thread.sleep((2 seconds).toMillis)
  println("finish" + Thread.currentThread().getName)
}
}
