package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.Node
import com.mattg.util.FakeRandom
import com.mattg.util.FakeFileUtil

import org.scalatest._

// This class also tests BaseEdgeSequencePathTypeFactory, which has the implementations of most of
// these methods.
class BasicPathTypeFactorySpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger
  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead("/graph/node_dict.tsv",
    "1\tnode1\n2\tnode2\n3\tnode3\n4\tnode4\n5\t100\n6\t50\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv",
    "1\trel1\n2\trel2\n3\trel3\n4\trel4\n5\t@ALIAS@\n")
  val graph = new GraphOnDisk("/graph/", outputter, fileUtil)

  val factory = new BasicPathTypeFactory(graph)
  val type1 = factory.fromString("-1-")
  val type12 = factory.fromString("-1-2-")
  val type1234 = factory.fromString("-1-2-3-4-")
  val type12_3_4 = factory.fromString("-1-2-_3-_4-")
  val type2 = factory.fromString("-2-")
  val type_2 = factory.fromString("-_2-")
  val type43 = factory.fromString("-4-3-")
  val type_4_3 = factory.fromString("-_4-_3-")

  "concatenatePathTypes" should "reverse the target path and combine the two" in {
    factory.concatenatePathTypes(type1, factory.emptyPathType) should be(type1)
    factory.concatenatePathTypes(factory.emptyPathType, type_2) should be(type2)
    factory.concatenatePathTypes(type1, type_2) should be(type12)
    factory.concatenatePathTypes(type12, type_4_3) should be(type1234)
    factory.concatenatePathTypes(type12, type43) should be(type12_3_4)
  }

  "emptyPathType" should "have correct equals results" in {
    factory.emptyPathType should be(factory.emptyPathType)
    factory.emptyPathType should not be(type12)
  }

  "encode" should "encode the edge types in the path" in {
    // The path we create is 1 -_1-> 2 -2-> 3 -3-> 4.  So the BasicPathType we should get from
    // this is -1-2-3-.
    val path = new Path(1, 5)
    path.addHop(2, 1, true)
    path.addHop(3, 2, false)
    path.addHop(4, 3, false)

    val pathTypes = factory.encode(path)
    pathTypes.length should be(1)
    pathTypes(0) should be(factory.fromString("-_1-2-3-"))
  }

  "nextHop" should "pick the right next nodes" in {
    var pathType = factory.fromString("-1-2-")

    val excluder = new FakeEdgeExcluder()
    val chiVertex = new FakeChiVertex(1)
    chiVertex.addInEdge(3, 3)
    chiVertex.addInEdge(2, 1)
    chiVertex.addOutEdge(1, 1)
    chiVertex.addOutEdge(2, 1)
    chiVertex.addOutEdge(3, 1)
    val vertex = new Vertex(chiVertex)

    val context = new FakeDrunkardContext()
    val random = new FakeRandom()

    var hopNum = 0
    var sourceId = 0

    // Ok, first, just make sure we get to the right next vertex.
    random.setNextInt(2)
    pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null) should be(3)
    random.setNextInt(0)

    // Now, make sure we reset if the walk ends up somewhere it's not allowed to be.
    excluder.setShouldExclude(true)
    pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null) should be(-1)
    excluder.setShouldExclude(false)

    // Make sure we reset if the hop number is too high.
    hopNum = 10
    pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null) should be(-1)
    hopNum = 0

    // Make sure we reset if there are no edges that match the path type.
    pathType = factory.fromString("-10-2-")
    pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null) should be(-1)

    // Now try some with a second path type, that has a reverse edge.
    pathType = factory.fromString("-_3-2-")
    pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null) should be(3)
  }

  "isLastHop" should "give the correct value from the number of hops" in {
    val pathType = factory.fromString("-1-2-3-")
    pathType.isLastHop(2) should be(true)
    pathType.isLastHop(1) should be(false)
  }

  "string encoding and decoding" should "be reversible" in {
    val pathType = factory.fromString("-1-_2-3-")
    pathType.encodeAsString() should be("-1-_2-3-")
    pathType.toString() should be("-1-_2-3-")
    pathType.encodeAsHumanReadableString(graph) should be("-rel1-_rel2-rel3-")
    factory.fromHumanReadableString("-rel1-_rel2-rel3-") should be(pathType)
  }
}

