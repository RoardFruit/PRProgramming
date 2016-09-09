var a=Set(1,2,3)
var b=Vector.empty[Int]
var c=Map(1->2,3->4,5->6,7->8)
//c--=Set(1,3)
//c.filter(x=>Seq(1,3).contains(x._1))
//c+=4->None

//c+=(5->4)

Set(1,2,3,4,5)--c.keys