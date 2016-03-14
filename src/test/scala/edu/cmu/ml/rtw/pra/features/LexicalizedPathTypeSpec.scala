package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.pra.graphs.Node
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.TestUtil
import com.mattg.util.TestUtil.Function

import org.json4s._
import org.json4s.JsonDSL._

class LexicalizedPathTypeSpec extends FlatSpecLike with Matchers {
  val nodeDict = new MutableConcurrentDictionary()
  nodeDict.getIndex("node1")
  nodeDict.getIndex("node2")
  nodeDict.getIndex("3:node3")
  nodeDict.getIndex("4:KEEP:node4")
  nodeDict.getIndex("5:REMOVE:node5")
  val edgeDict = new MutableConcurrentDictionary()
  edgeDict.getIndex("rel1")
  edgeDict.getIndex("rel2")

  val graph = new GraphInMemory(Array[Node](), nodeDict, edgeDict)
  val factory = new LexicalizedPathTypeFactory(JNothing, graph)

  "stringDescription" should "encode nodes and edges with null graph" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false), JNothing)
    pathType.stringDescription(null, Map()) should be("-_1->3-2->4")
  }

  it should "use the node and edge dictionaries when provided" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(1, 2), Array(true, false), JNothing)
    pathType.stringDescription(graph, Map()) should be("-_rel1->node1-rel2->node2")
  }

  it should "work correctly on a combined path type" in {
    val combined = new LexicalizedPathType(Array(1, 2, 3, 4), Array(6, 7, 8), Array(true, false,
      true, false), JNothing)
    combined.stringDescription(null, Map()) should be("-_1->6-2->7-_3->8-4->")
  }

  it should "leave colons in when instructed" in {
    val params: JValue = ("remove colon" -> "no")
    val pathType = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false), params)
    pathType.stringDescription(graph, Map()) should be("-_rel1->3:node3-rel2->4:KEEP:node4")
  }

  it should "remove colons when instructed" in {
    val params: JValue = ("remove colon" -> "yes")
    val pathType = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false), params)
    pathType.stringDescription(graph, Map()) should be("-_rel1->node3-rel2->node4")
  }

  it should "filter colons when instructed" in {
    val params: JValue = ("remove colon" -> "filter")
    val pathType = new LexicalizedPathType(Array(1, 2), Array(4, 5), Array(true, false), params)
    pathType.stringDescription(graph, Map()) should be("-_rel1->node4-rel2->")
  }

  it should "filter colons by default" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(4, 5), Array(true, false), JNothing)
    pathType.stringDescription(graph, Map()) should be("-_rel1->node4-rel2->")
  }

  it should "crash when filtering with bad formatting" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false), JNothing)
    TestUtil.expectError(classOf[IllegalStateException], "must be KEEP or REMOVE", new Function() {
      def call() {
        pathType.stringDescription(graph, Map())
      }
    })
  }

  "encodeAsHumanReadableStringWithoutNodes" should "look like a normal PRA path" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false), JNothing)
    pathType1.encodeAsHumanReadableStringWithoutNodes(graph) should be("-_rel1-rel2-")
    val pathType2 = new LexicalizedPathType(Array(1, 2), Array(3), Array(true, false), JNothing)
    pathType2.encodeAsHumanReadableStringWithoutNodes(graph) should be("-_rel1-rel2-")
  }

  "fromString" should "successfully parse an encoded string" in {
    val pathType = new LexicalizedPathType(Array(1, 2), Array(3, 4), Array(true, false), JNothing)
    factory.fromString("-_1->3-2->4") should be(pathType)
  }

  it should "successfully parse a combined path type" in {
    val combined = new LexicalizedPathType(Array(1, 2, 3, 4), Array(6, 7, 8), Array(true, false,
      true, false), JNothing)
    factory.fromString("-_1->6-2->7-_3->8-4->") should be(combined)
  }

  "concatenatePathTypes" should "reverse the path to target and concatenate the path types" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false), JNothing)
    val pathType2 = new LexicalizedPathType(Array(4, 3), Array(8, 7), Array(true, false), JNothing)
    val combined = new LexicalizedPathType(Array(1, 2, 3, 4), Array(6, 7, 8), Array(true, false,
      true, false), JNothing)
    factory.concatenatePathTypes(pathType1, pathType2) should be(combined)
  }

  it should "handle empty target paths" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false), JNothing)
    val pathType2 = factory.emptyPathType()
    val combined = new LexicalizedPathType(Array(1, 2), Array(6), Array(true, false), JNothing)
    factory.concatenatePathTypes(pathType1, pathType2) should be(combined)
  }

  it should "handle empty source paths" in {
    val pathType1 = factory.emptyPathType()
    val pathType2 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false), JNothing)
    val combined = new LexicalizedPathType(Array(2, 1), Array(6), Array(true, false), JNothing)
    factory.concatenatePathTypes(pathType1, pathType2) should be(combined)
  }

  it should "crash when ending nodes don't match" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 7), Array(true, false), JNothing)
    val pathType2 = new LexicalizedPathType(Array(4, 3), Array(8, 9), Array(true, false), JNothing)
    TestUtil.expectError(classOf[IllegalStateException], "nodes don't match", new Function() {
      def call() {
        factory.concatenatePathTypes(pathType1, pathType2)
      }
    })
  }

  it should "crash if the source or target path is already combined" in {
    val pathType1 = new LexicalizedPathType(Array(1, 2), Array(6, 6), Array(true, false), JNothing)
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

  "reversePathType" should "create a new path type in the opposite direction" in {
    val pathType = factory.fromString("-_1->3-2->")
    val reversed = factory.reversePathType(pathType)
    reversed should be(factory.fromString("-_2->3-1->"))
  }

}
