
import akka.actor.FSM.Failure
import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.event.LoggingReceive
import akka.pattern.{ask, pipe}
import com.typesafe.config.ConfigFactory

import scala.util.{ Random}


/**
 * Created by Mr.gong on 2016/5/21.
 */
object text extends App{
  case class Persis(id: Long)
  case class Persist(id: Long)
  case object calulate
  class PersistenceException extends Exception("Persistence failure")
  class Per(flaky: Boolean) extends Actor {
    def receive = {
      case Persis(id) =>
        if (!flaky && Random.nextBoolean()) sender ! Persist(id)
        else throw new PersistenceException
    }
  }

  class mytest extends Actor{
    import context.dispatcher
    val per1=context.actorOf(Props(classOf[Per],false),"per")
    override val supervisorStrategy=OneForOneStrategy() {
      case _=> restart
    }
    implicit val timeout = Timeout(3 seconds)
    def receive={
      case Persist(id)=>
        println("succ"+id)
      case calulate=>
       (per1?Persis(0)).mapTo[Persist].recover{case ex=>Failure(ex)}.pipeTo(self)
       case Failure(ex)=>
       println("fail"+ex)

    }
  }
  implicit val timeout = Timeout(3 seconds)
  implicit val sender=system
  val system = ActorSystem("System")
  val my=system.actorOf(Props(classOf[mytest]),"mytext")
  my!calulate
}
