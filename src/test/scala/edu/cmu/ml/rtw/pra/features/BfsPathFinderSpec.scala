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

  def makeFinder(params: JValue = JNothing) = new NodePairBfsPathFinder(params, outputter, fileUtil)

  "findPaths" should "find correct subgraphs with simple parameters" in {
    val finder = makeFinder()
    finder.findPaths(data)
    val results53 = finder.results(instance)

    results53 should be(Set(
      // 7 Source paths
      Path(5, Array(1), Array(3), Array(false)),
      Path(5, Array(1), Array(3), Array(false)),
      Path(5, Array(1, 2), Array(3, 1), Array(false, false)),
      Path(5, Array(1, 3), Array(3, 1), Array(false, false)),
      Path(5, Array(1, 4), Array(3, 2), Array(false, false)),
      Path(5, Array(1, 2), Array(3, 4), Array(false, true)),
      Path(5, Array(1, 6), Array(3, 1), Array(false, true)),
      Path(5, Array(1, 5), Array(3, 3), Array(false, true)),
      // 7 Target paths
      Path(3, Array(1), Array(1), Array(true)),
      Path(3, Array(1, 2), Array(1, 1), Array(true, false)),
      Path(3, Array(1, 3), Array(1, 1), Array(true, false)),
      Path(3, Array(1, 4), Array(1, 2), Array(true, false)),
      Path(3, Array(1, 2), Array(1, 4), Array(true, true)),
      Path(3, Array(1, 6), Array(1, 1), Array(true, true)),
      Path(3, Array(1, 5), Array(1, 3), Array(true, true)),
      // 8 Combined paths - some of these contain loops.  If loop detection is ever added, these
      // tests should be revisited.
      Path(5, Array(1, 2, 1, 3), Array(3, 1, 1, 1), Array(false, false, true, false)),
      Path(5, Array(1, 3, 1, 3), Array(3, 1, 1, 1), Array(false, false, true, false)),
      Path(5, Array(1, 6, 1, 3), Array(3, 1, 1, 1), Array(false, true, false, false)),
      Path(5, Array(1, 2, 1, 3), Array(3, 1, 4, 1), Array(false, false, false, false)),
      Path(5, Array(1, 4, 1, 3), Array(3, 2, 2, 1), Array(false, false, true, false)),
      Path(5, Array(1, 5, 1, 3), Array(3, 3, 3, 1), Array(false, true, false, false)),
      Path(5, Array(1, 2, 1, 3), Array(3, 4, 4, 1), Array(false, true, false, false)),
      Path(5, Array(1, 2, 1, 3), Array(3, 4, 1, 1), Array(false, true, true, false))
    ))
  }

  it should "observe the max fan out" in {
    val finder = makeFinder(("max fan out" -> 2))
    finder.findPaths(data)

    // These results should be the same as above, except missing anything that uses relation 1 at
    // node 1.  However, we can use it as an _intermediate_ node when combining paths, so we should
    // still see one -3-1- in here.
    val results53 = finder.results(instance)

    // 4 Source paths
    results53 should be(Set(
      Path(5, Array(1), Array(3), Array(false)),
      Path(5, Array(1, 4), Array(3, 2), Array(false, false)),
      Path(5, Array(1, 2), Array(3, 4), Array(false, true)),
      Path(5, Array(1, 5), Array(3, 3), Array(false, true)),
      // 4 Target paths
      Path(3, Array(1), Array(1), Array(true)),
      Path(3, Array(1, 4), Array(1, 2), Array(true, false)),
      Path(3, Array(1, 2), Array(1, 4), Array(true, true)),
      Path(3, Array(1, 5), Array(1, 3), Array(true, true)),
      // 4 Combined paths - some of these contain loops.  If loop detection is ever added, these
      // tests should be revisited.
      Path(5, Array(1, 3), Array(3, 1), Array(false, false)),
      Path(5, Array(1, 4, 1, 3), Array(3, 2, 2, 1), Array(false, false, true, false)),
      Path(5, Array(1, 5, 1, 3), Array(3, 3, 3, 1), Array(false, true, false, false)),
      Path(5, Array(1, 2, 1, 3), Array(3, 4, 4, 1), Array(false, true, false, false))
    ))
  }
}
