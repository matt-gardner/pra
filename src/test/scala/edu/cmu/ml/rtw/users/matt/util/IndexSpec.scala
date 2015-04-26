package edu.cmu.ml.rtw.users.matt.util

import org.scalatest._

class IndexSpec extends FlatSpecLike with Matchers {
  val index = new Index[String](new StringParser(), false, new FileUtil());

  "getIndex" should "insert if element isn't present" in {
    index.clear
    index.getIndex("string 1") should be(1)
    index.getIndex("string 2") should be(2)
    index.getIndex("string 3") should be(3)
    index.getIndex("string 4") should be(4)
    index.getIndex("string 1") should be(1)
    index.getIndex("string 2") should be(2)
    index.getIndex("string 3") should be(3)
    index.getIndex("string 4") should be(4)
    index.getKey(1) should be("string 1")
    index.getKey(2) should be("string 2")
    index.getKey(3) should be("string 3")
    index.getKey(4) should be("string 4")
  }

  it should "work will multiple concurrent inserts" in {
    index.clear
    println("Concurrent test")
    (1 to 1000).toList.par.foreach(i => {
      val j = index.getIndex("string")
      index.getKey(j) should be("string")
    })
    println("Second concurrent test")
    (1 to 1000).toList.par.foreach(i => {
      val j = index.getIndex(s"string $i")
      index.getKey(j) should be(s"string $i")
    })
  }

  "clear" should "clear the index" in {
    index.clear
    index.getIndex("string 1") should be(1)
    index.clear
    index.getIndex("string 2") should be(1)
  }
}
