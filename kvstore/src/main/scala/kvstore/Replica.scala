package kvstore

import akka.actor.{ OneForOneStrategy, Props, ActorRef, Actor }
import kvstore.Arbiter._
import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.Restart
import scala.annotation.tailrec
import akka.pattern.{ ask, pipe }
import akka.actor.Terminated
import scala.concurrent.duration._
import akka.actor.PoisonPill
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.util.Timeout

object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation
  case object Resendpersist
  case class timeout(id:Long)
  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  
  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  val persist=context.actorOf(persistenceProps)

  var idtosender=Map.empty[Long,ActorRef]

  var pending = Vector.empty[Persist]

  var reping=Map.empty[Long,Set[ActorRef]]

  context.system.scheduler.schedule(0 millisecond,100 millisecond,self,Resendpersist)

  arbiter!Join

  def receive = {
    case JoinedPrimary   => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    case Insert(key, value, id)=>
//      sender!OperationAck(id)
      kv+=key->value
     replicators foreach (_!Replicate(key,Some(value), id))
     reping+=id->(replicators+self)
     persist!Persist(key,Some(value), id)
     pending=pending:+Persist(key,Some(value),id)
     idtosender+=id->sender
      context.system.scheduler.scheduleOnce(1 second,self,timeout(id))
    case Remove(key, id)=>
      kv-=key
      replicators foreach (_!Replicate(key,None, id))
      reping+=id->(replicators+self)
      persist!Persist(key,None, id)
      pending=pending:+Persist(key,None,id)
      idtosender+=id->sender
      context.system.scheduler.scheduleOnce(1 second,self,timeout(id))
    case Get(key, id)=>
      val value:Option[String]=try {
        Some(kv(key))
      }
      catch {
        case _:Exception=>None
      }
        sender!GetResult(key,value,id)
    case Persisted(key,id)=>
      pending=pending.filter{case Persist(_,_,seq)=>seq!=id}
      reping=reping.updated(id,reping(id)-self)
      if(reping(id).isEmpty) {
        idtosender(id)!OperationAck(id)
        idtosender-=id
        reping-=id
      }
    case Replicated(key,id)=>
      reping=reping.updated(id,reping(id)-sender)
      if(reping(id).isEmpty) {
        idtosender(id)!OperationAck(id)
        idtosender-=id
        reping-=id
      }
    case Resendpersist=>
      pending.foreach(persist!_)
    case timeout(id)=>
      /*pending foreach {
        case Persist(_,_,id)=>idtosender(id)!OperationFailed(id)
        idtosender-=id
      }*/

      pending=pending.filter(_.id!=id)
     try{
       reping(id)
       idtosender(id)!OperationFailed(id)
       idtosender-=id
       reping-=id
     }
      catch {
        case _=>
      }
    case Replicas(crep)=>
      val replicas=crep-self
      if(replicas.size>secondaries.keys.size)
        replicas--secondaries.keys foreach { x=>
        val replicator=context.actorOf(Replicator.props(x))
        secondaries+=x->replicator
        replicators+=replicator
        kv foreach {case(k,v)=>replicator!Replicate(k,Some(v),-1)}
      }
      else secondaries.keySet--replicas foreach{x=>
        val replicator=secondaries(x)
        context.stop(replicator)
        reping=reping.map{case(id,rep)=>(id,rep-replicator)}
        reping foreach {case(id,rep)=>if(rep.isEmpty) idtosender(id)!OperationAck(id)}
        reping=reping.filter(_._2.nonEmpty)
      }
  }

  /* TODO Behavior for the replica role. */
  var expectedId=0L

  val replica: Receive = {
    case Get(key, id)=>
      val value:Option[String]=try {
        Some(kv(key))
      }
      catch {
        case _:Exception=>None
      }
      sender!GetResult(key,value,id)
    case Snapshot(key, valueOption, seq)=>
      if(seq>expectedId) ()
      else if(seq<expectedId) sender!SnapshotAck(key, seq)
      else {
        if (!pending.contains(Persist(key, valueOption, seq))) {
          valueOption match {
            case None => kv -= key
            case Some(x) => kv += key -> x
          }
          persist ! Persist(key, valueOption, seq)
          pending = pending :+ Persist(key, valueOption, seq)
          idtosender += seq -> sender
        }
      }
    case Persisted(key,id)=>
       idtosender(id)!SnapshotAck(key,id)
       idtosender-=id
       pending=pending.filter{case Persist(_,_,seq)=>seq!=id}
       expectedId+=1
    case Resendpersist=>
       pending.foreach(persist!_)
  }

}

