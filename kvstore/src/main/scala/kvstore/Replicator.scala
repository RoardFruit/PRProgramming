package kvstore

import akka.actor.{ReceiveTimeout, Props, Actor, ActorRef}
import scala.concurrent.duration._

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)
  
  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)
  case object Resendsnap
  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import Replica._
  import context.dispatcher

  context.system.scheduler.schedule(10 millisecond,100 millisecond,self,Resendsnap)
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]

  var _seqCounter = 0L
  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

 /* def reactoSnap(x:Map[Long, (ActorRef, Replicate)],snapshot: SnapshotAck):Map[Long, (ActorRef, Replicate)]={
    if (x.isEmpty) Map.empty
    else x.head match {
      case (seq,(sender,Replicate(key, valueOption, id)))=>
        if(key==snapshot.key&&seq<=snapshot.seq) {
         if(id>=0) sender forward Replicated(key, id)
          reactoSnap(x.tail,snapshot)
        }
        else reactoSnap(x.tail,snapshot)+x.head
      }
    }*/

  /* TODO Behavior for the Replicator. */
  def receive: Receive = {
    case Replicate(key, valueOption, id) =>
      val sp=Snapshot(key, valueOption, _seqCounter)
      replica!sp
      pending=pending.filter(_.key!=sp.key):+sp
      acks+=nextSeq->(sender,Replicate(key, valueOption, id))
    case SnapshotAck(key, seq) =>
      pending=pending.filter(_.key!=key)
      acks foreach{
        case (seq1,(sender1,Replicate(key1,_, id)))=>
      if(key==key1&&seq1<=seq) {
        if(id>=0) sender1 ! Replicated(key, id)
        acks-=seq1
      }
      }
    case Resendsnap=>
      pending.foreach(replica!_)
  }


}
