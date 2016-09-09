/**
 * Copyright (C) 2013-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package kvstore

import akka.actor.{ Actor, Props, ActorRef, ActorSystem }
import akka.testkit.{ TestProbe, ImplicitSender, TestKit }
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import scala.concurrent.duration._
import org.scalatest.FunSuiteLike
import org.scalactic.ConversionCheckedTripleEquals

import scala.util.Random

class IntegrationSpec(_system: ActorSystem) extends TestKit(_system)
    with FunSuiteLike
        with Matchers
    with BeforeAndAfterAll
    with ConversionCheckedTripleEquals
    with ImplicitSender
    with Tools {

  import Replica._
  import Replicator._
  import Arbiter._

  def this() = this(ActorSystem("ReplicatorSpec"))

  override def afterAll: Unit = system.shutdown()

  /*
   * Recommendation: write a test case that verifies proper function of the whole system,
   * then run that with flaky Persistence and/or unreliable communication (injected by
   * using an Arbiter variant that introduces randomly message-dropping forwarder Actors).
   */
  test("case1:Primary and secondaries must work in concert when communication to secondaries is unreliable"){

    class RefWrappingActor(target: ActorRef) extends Actor{
      def receive = { case msg =>if(Random.nextBoolean())
         target forward msg }
    }

    val arbiter = TestProbe()
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = true)), "case1-primary")
    Thread.sleep(100)
    arbiter.expectMsg(Join)
    arbiter.send(primary, JoinedPrimary)
    val second=system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = true)), "case1-second")
    arbiter.expectMsg(Join)
    arbiter.send(second, JoinedSecondary)
    val unsecond=system.actorOf(Props(new RefWrappingActor(second)),"case1-unsecond")
    val Third=system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = true)), "case1-Third")
    arbiter.expectMsg(Join)
    arbiter.send(Third, JoinedSecondary)
    val unThird=system.actorOf(Props(new RefWrappingActor(Third)),"case1-unthird")
    arbiter.send(primary, Replicas(Set(primary,unsecond,unThird)))
    val client = session(primary)
    (1 to 50).foreach{i=> client.setAcked("k"+i,"v"+i)}
  }


test("case2:Primary and secondaries must work in concert when communication to secondaries is reliable"){

val arbiter = system.actorOf(Props(classOf[Arbiter]),"case2-Arbiter")
//val arbiter =TestProbe()
val primary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case2-primary")
  //arbiter.send(primary, JoinedPrimary)
  Thread.sleep(100)
val secondery=system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)),"case2-second")
  //arbiter.send(secondery, JoinedSecondary)
 // arbiter.send(primary, Replicas(Set(primary,secondery)))
val client = session(primary)
  (1 to 50).foreach{i=> client.setAcked("k"+i,"v"+i)}
}
}