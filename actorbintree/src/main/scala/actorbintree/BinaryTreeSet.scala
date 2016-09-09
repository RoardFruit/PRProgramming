/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case x:Operation => root!x
    case GC=>
      val newroot=context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))
      root!CopyTo(newroot)
      context.become(garbageCollecting(newroot))
  }

 /* def next(quene:Queue[Operation]):Receive={
    case Unit=>
      if(quene.isEmpty) context.become(normal)
     else {
        root ! quene.head
        context.become(next(quene.tail))
      }
    case x:Operation=>
      context.become(next(quene :+ x))
  }*/
  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
    case x:Operation=>pendingQueue=pendingQueue :+ x
    case CopyFinished=>
    sender!PoisonPill
      root=newRoot
      context.become(normal)
      pendingQueue foreach(root!_)
      pendingQueue = Queue.empty[Operation]
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {
    case Insert(req, i, x)=>
      if(elem==x) {
      removed=false
      req!OperationFinished(i)
    }
      else if(x<elem) {
        if (subtrees.contains(Left)) subtrees(Left)!Insert(req, i, x)
        else {
          subtrees=subtrees + (Left -> context.actorOf(BinaryTreeNode.props(x, initiallyRemoved = false)))
          req!OperationFinished(i)
        }
      }
      else {
        if (subtrees.contains(Right)) subtrees(Right)!Insert(req, i, x)
        else {
          subtrees=subtrees + (Right -> context.actorOf(BinaryTreeNode.props(x, initiallyRemoved = false)))
          req!OperationFinished(i)
        }
      }
    case Remove(req,i,x)=>
      if(elem==x) {
        removed=true
        req!OperationFinished(i)
      }
      else if(x<elem) {
        if (subtrees.contains(Left)) subtrees(Left)!Remove(req, i, x)
        else req!OperationFinished(i)
      }
    else {
      if (subtrees.contains(Right)) subtrees(Right)!Remove(req, i, x)
      else req!OperationFinished(i)
    }
    case Contains(req,i,x)=>
      if(elem==x) req!ContainsResult(i,!removed)
      else if(x<elem) {
        if (subtrees.contains(Left)) subtrees(Left)!Contains(req,i,x)
        else req!ContainsResult(i,false)
      }
      else {
        if (subtrees.contains(Right)) subtrees(Right)!Contains(req,i,x)
        else req!ContainsResult(i,false)
      }
    case CopyTo(x)=>
      if(!removed) x!Insert(self, 0, elem)
      subtrees foreach {case(p,a)=>a!CopyTo(x)}
      if(removed&&subtrees.isEmpty) context.parent!CopyFinished
     else context.become(copying(subtrees.values.toSet,removed))
}

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = {
    case x:OperationFinished=>
      if(expected.isEmpty) context.parent!CopyFinished
      else context.become(copying(expected,true))
    case CopyFinished=>
      val ex=expected-sender
     // sender!PoisonPill
      if(!insertConfirmed) context.become(copying(ex,false))
      else if(ex.isEmpty) context.parent!CopyFinished
      else context.become(copying(ex,true))
  }


}
