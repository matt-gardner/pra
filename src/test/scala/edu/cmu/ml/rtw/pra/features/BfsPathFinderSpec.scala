package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.FakeFileUtil
import com.mattg.util.Pair

class BfsPathFinderSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger

  val fileUtil = new FakeFileUtil

  val graphFilename = "/graph/graph_chi/edges.tsv"
  val graphFileContents = "1\t2\t1\n" +
      "1\t3\t1\n" +
      "1\t4\t2\n" +
      "2\t1\t4\n" +
      "5\t1\t3\n" +
      "6\t1\t1\n"
  val nodeDictContents = "1\tnode1\n" +
      "2\tnode2\n" +
      "3\tnode3\n" +
      "4\tnode4\n" +
      "5\tnode5\n" +
      "6\tnode6\n"
  val edgeDictContents = "1\trel1\n" +
      "2\trel2\n" +
      "3\trel3\n" +
      "4\trel4\n" +
      "5\trel5\n"

  fileUtil.addFileToBeRead(graphFilename, graphFileContents)
  fileUtil.addFileToBeRead("/graph/node_dict.tsv", nodeDictContents)
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv", edgeDictContents)

  val relation = "rel1"
  val graph = new GraphOnDisk("/graph/", outputter, fileUtil)
  val factory = new BasicPathTypeFactory(graph)
  val instance = new NodePairInstance(5, 3, true, graph)
  val data = new Dataset[NodePairInstance](Seq(instance), fileUtil)

  def makeFinder(params: JValue = JNothing, relation: String = relation) =
    new NodePairBfsPathFinder(params, relation, RelationMetadata.empty, outputter, fileUtil)

  "findPaths" should "find correct subgraphs with simple parameters" in {
    val finder = makeFinder()
    finder.findPaths(data)
    val results53 = finder.results(instance)

    // 6 Source paths
    results53(factory.fromString("-3-")).size should be(1)
    results53(factory.fromString("-3-")) should contain((5, 1))
    results53(factory.fromString("-3-1-")).size should be(2)
    results53(factory.fromString("-3-1-")) should contain((5, 2))
    results53(factory.fromString("-3-1-")) should contain((5, 3))
    results53(factory.fromString("-3-2-")).size should be(1)
    results53(factory.fromString("-3-2-")) should contain((5, 4))
    results53(factory.fromString("-3-_4-")).size should be(1)
    results53(factory.fromString("-3-_4-")) should contain((5, 2))
    results53(factory.fromString("-3-_1-")).size should be(1)
    results53(factory.fromString("-3-_1-")) should contain((5, 6))
    results53(factory.fromString("-3-_3-")).size should be(1)
    results53(factory.fromString("-3-_3-")) should contain((5, 5))

    // 6 Target paths
    results53(factory.fromString("-_1-")).size should be(1)
    results53(factory.fromString("-_1-")) should contain((3, 1))
    results53(factory.fromString("-_1-1-")).size should be(2)
    results53(factory.fromString("-_1-1-")) should contain((3, 2))
    results53(factory.fromString("-_1-1-")) should contain((3, 3))
    results53(factory.fromString("-_1-2-")).size should be(1)
    results53(factory.fromString("-_1-2-")) should contain((3, 4))
    results53(factory.fromString("-_1-_4-")).size should be(1)
    results53(factory.fromString("-_1-_4-")) should contain((3, 2))
    results53(factory.fromString("-_1-_1-")).size should be(1)
    results53(factory.fromString("-_1-_1-")) should contain((3, 6))
    results53(factory.fromString("-_1-_3-")).size should be(1)
    results53(factory.fromString("-_1-_3-")) should contain((3, 5))

    // 7 Combined paths - some of these contain loops.  If loop detection is ever added, these
    // tests should be revisited.
    results53(factory.fromString("-3-1-_1-1-")).size should be(1)
    results53(factory.fromString("-3-1-_1-1-")) should contain((5, 3))
    results53(factory.fromString("-3-1-4-1-")).size should be(1)
    results53(factory.fromString("-3-1-4-1-")) should contain((5, 3))
    results53(factory.fromString("-3-2-_2-1-")).size should be(1)
    results53(factory.fromString("-3-2-_2-1-")) should contain((5, 3))
    results53(factory.fromString("-3-_4-4-1-")).size should be(1)
    results53(factory.fromString("-3-_4-4-1-")) should contain((5, 3))
    results53(factory.fromString("-3-_4-_1-1-")).size should be(1)
    results53(factory.fromString("-3-_4-_1-1-")) should contain((5, 3))
    results53(factory.fromString("-3-_1-1-1-")).size should be(1)
    results53(factory.fromString("-3-_1-1-1-")) should contain((5, 3))
    results53(factory.fromString("-3-_3-3-1-")).size should be(1)
    results53(factory.fromString("-3-_3-3-1-")) should contain((5, 3))

    // 6 + 6 + 7 = 19 total paths
    results53.size should be(19)
  }

  it should "exclude edges when specified" in {
    val instance = new NodePairInstance(1, 3, true, graph)
    val data = new Dataset[NodePairInstance](Seq(instance), fileUtil)
    val finder = makeFinder(JNothing, "rel1")
    finder.findPaths(data)
    val results13 = finder.results(instance)
    val badPath = factory.fromString("-1-")
    results13(badPath) should not contain((1, 3))
  }

  it should "observe the max fan out" in {
    val finder = makeFinder(("max fan out" -> 2))
    finder.findPaths(data)

    // These results should be the same as above, except missing anything that uses relation 1 at
    // node 1.  However, we can use it as an _intermediate_ node when combining paths, so we should
    // still see one -3-1- in here.
    val results53 = finder.results(instance)

    // 4 Source paths
    results53(factory.fromString("-3-")).size should be(1)
    results53(factory.fromString("-3-")) should contain((5, 1))
    results53(factory.fromString("-3-2-")).size should be(1)
    results53(factory.fromString("-3-2-")) should contain((5, 4))
    results53(factory.fromString("-3-_4-")).size should be(1)
    results53(factory.fromString("-3-_4-")) should contain((5, 2))
    results53(factory.fromString("-3-_3-")).size should be(1)
    results53(factory.fromString("-3-_3-")) should contain((5, 5))

    // 4 Target paths
    results53(factory.fromString("-_1-")).size should be(1)
    results53(factory.fromString("-_1-")) should contain((3, 1))
    results53(factory.fromString("-_1-2-")).size should be(1)
    results53(factory.fromString("-_1-2-")) should contain((3, 4))
    results53(factory.fromString("-_1-_4-")).size should be(1)
    results53(factory.fromString("-_1-_4-")) should contain((3, 2))
    results53(factory.fromString("-_1-_3-")).size should be(1)
    results53(factory.fromString("-_1-_3-")) should contain((3, 5))

    // 4 Combined paths - some of these contain loops.  If loop detection is ever added, these
    // tests should be revisited.
    results53(factory.fromString("-3-1-")).size should be(1)
    results53(factory.fromString("-3-1-")) should contain((5, 3))
    results53(factory.fromString("-3-2-_2-1-")).size should be(1)
    results53(factory.fromString("-3-2-_2-1-")) should contain((5, 3))
    results53(factory.fromString("-3-_4-4-1-")).size should be(1)
    results53(factory.fromString("-3-_4-4-1-")) should contain((5, 3))
    results53(factory.fromString("-3-_3-3-1-")).size should be(1)
    results53(factory.fromString("-3-_3-3-1-")) should contain((5, 3))

    // 4 + 4 + 4 = 12 total paths
    results53.size should be(12)
  }
}
