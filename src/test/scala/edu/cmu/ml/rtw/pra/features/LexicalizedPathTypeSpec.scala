package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

class LexicalizedPathTypeSpec extends FlatSpecLike with Matchers {
  val nodeDict = new Dictionary()
  nodeDict.getIndex("node1")
  nodeDict.getIndex("node2")
  val edgeDict = new Dictionary()
  edgeDict.getIndex("rel1")
  edgeDict.getIndex("rel2")

  val factory = new LexicalizedPathTypeFactory

  "stringDescription" should "encode nodes and edges with null dictionaries" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false))
    pathType.stringDescription(null, null) should be("-_1->3-2->4")
  }

  it should "use the node and edge dictionaries when provided" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(1, 2), Array(true, false))
    pathType.stringDescription(edgeDict, nodeDict) should be("-_rel1->node1-rel2->node2")
  }

  it should "work correctly on a combined path type" in {
    val combined = new LexicalizedPathType(Array(1, 2, 3, 4), Array(6, 7, 8), Array(true, false, true, false))
    combined.stringDescription(null, null) should be("-_1->6-2->7-_3->8-4->")
  }

  "fromString" should "successfully parse an encoded string" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false))
    factory.fromString("-_1->3-2->4") should be(pathType)
  }

  it should "successfully parse a combined path type" in {
    val combined = new LexicalizedPathType(Array(1, 2, 3, 4), Array(6, 7, 8), Array(true, false, true, false))
    factory.fromString("-_1->6-2->7-_3->8-4->") should be(combined)
  }

  "concatenatePathTypes" should "reverse the path to target and concatenate the path types" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false))
    val pathType2 = new LexicalizedPathType(Array(4, 3), Array(8, 7), Array(true, false))
    val combined = new LexicalizedPathType(Array(1, 2, 3, 4), Array(6, 7, 8), Array(true, false, true, false))
    factory.concatenatePathTypes(pathType1, pathType2) should be(combined)
  }

  it should "handle empty target paths" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false))
    val pathType2 = factory.emptyPathType()
    val combined = new LexicalizedPathType(Array(1, 2), Array(6), Array(true, false))
    factory.concatenatePathTypes(pathType1, pathType2) should be(combined)
  }

  it should "handle empty source paths" in {
    val pathType1 = factory.emptyPathType()
    val pathType2 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false))
    val combined = new LexicalizedPathType(Array(2, 1), Array(6), Array(true, false))
    factory.concatenatePathTypes(pathType1, pathType2) should be(combined)
  }

  it should "crash when ending nodes don't match" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false))
    val pathType2 = new LexicalizedPathType(Array(4, 3), Array(8, 9), Array(true, false))
    TestUtil.expectError(classOf[IllegalStateException], "nodes don't match", new Function() {
      def call() {
        factory.concatenatePathTypes(pathType1, pathType2)
      }
    })
  }

  it should "crash if the source or target path is already combined" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 6), Array(true, false))
    val pathType2 = factory.emptyPathType()
    val combined = factory.concatenatePathTypes(pathType1, pathType2)
    TestUtil.expectError(classOf[IllegalStateException], "end in an edge", new Function() {
      def call() {
        factory.concatenatePathTypes(pathType1, combined)
      }
    })
    TestUtil.expectError(classOf[IllegalStateException], "end in an edge", new Function() {
      def call() {
        factory.concatenatePathTypes(combined, pathType1)
      }
    })
  }
}
