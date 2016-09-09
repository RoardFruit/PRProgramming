package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }

  property("gen1") = forAll { (h: H) =>
    val m = if (isEmpty(h)) 0 else findMin(h)
    findMin(insert(m, h))==m
  }

  property("min2") = forAll { (a:Int,b:Int) =>
    val h = insert(b,insert(a,empty))
    findMin(h)==a.min(b)
  }

  property("del1") = forAll { a:Int =>
    val h = insert(a, empty)
    deleteMin(h)==empty
  }

  property("sort1") = forAll { h: H =>
    def iter(h: H): List[Int] = {
      if (isEmpty(h)) Nil
      else {
        val m = findMin(h)
        m :: iter(deleteMin(h))
      }
    }
    val l=iter(h)
    l.sorted==l
  }

  property("sort2") = forAll { (h1: H,h2: H) =>
    def iter(h: H): List[Int] = {
      if (isEmpty(h)) Nil
      else {
        val m = findMin(h)
        m :: iter(deleteMin(h))
      }
    }
    val l=iter(meld(h1,h2))
    val l1=iter(h1)
    val l2=iter(h2)
    l.sorted==(l1++l2).sorted
  }


  property("min3") = forAll { (h1:H,h2:H) =>
   val h=meld(h1,h2)
    findMin(h)==findMin(h1).min(findMin(h2))
  }

  lazy val genHeap: Gen[H] = for {
      x<-arbitrary[Int]
      y<-oneOf(const(empty), genHeap)
    } yield insert(x,y)

implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
