package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.FakeFileUtil
import com.mattg.util.FileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

import gnu.trove.{TIntObjectHashMap => TMap}

class GraphOnDiskSpec extends FlatSpecLike with Matchers {

  val outputter = Outputter.justLogger
  val graphDir = "src/test/resources/fake_graph/"
  val graphFilename = graphDir + "graph_chi/edges.tsv"
  val graphFileContents = "1\t2\t1\n" +
      "1\t3\t1\n" +
      "1\t4\t2\n" +
      "2\t1\t4\n" +
      "5\t1\t3\n" +
      "6\t1\t1\n"
  val nodeDictFile = graphDir + "node_dict.tsv"
  val nodeDictFileContents = "1\t1\n2\t2\n3\t3\n4\t4\n5\t5\n6\t6\n"
  val edgeDictFile = graphDir + "edge_dict.tsv"
  val edgeDictFileContents = "1\t1\n2\t2\n3\t3\n4\t4\n"

  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(graphFilename, graphFileContents)
  fileUtil.addFileToBeRead(nodeDictFile, nodeDictFileContents)
  fileUtil.addFileToBeRead(edgeDictFile, edgeDictFileContents)

  def testGraphIsCorrect(graph: Graph) {
    graph.getNumNodes() should be(7)
    graph.getNode(0).edges should be(new TMap())
    graph.getNode(1).edges.size should be(4)
    graph.getNode(1).edges.get(1)._1.size should be(1)
    graph.getNode(1).edges.get(1)._1.get(0) should be(6)
    graph.getNode(1).edges.get(1)._2.size should be(2)
    graph.getNode(1).edges.get(1)._2.get(0) should be(2)
    graph.getNode(1).edges.get(1)._2.get(1) should be(3)
    graph.getNode(1).edges.get(2)._1.size should be(0)
    graph.getNode(1).edges.get(2)._2.size should be(1)
    graph.getNode(1).edges.get(2)._2.get(0) should be(4)
    graph.getNode(1).edges.get(3)._1.size should be(1)
    graph.getNode(1).edges.get(3)._1.get(0) should be(5)
    graph.getNode(1).edges.get(3)._2.size should be(0)
    graph.getNode(1).edges.get(4)._1.size should be(1)
    graph.getNode(1).edges.get(4)._1.get(0) should be(2)
    graph.getNode(1).edges.get(4)._2.size should be(0)
    graph.getNode(2).edges.size should be(2)
    graph.getNode(2).edges.get(1)._1.size should be(1)
    graph.getNode(2).edges.get(1)._1.get(0) should be(1)
    graph.getNode(2).edges.get(1)._2.size should be(0)
    graph.getNode(2).edges.get(4)._1.size should be(0)
    graph.getNode(2).edges.get(4)._2.size should be(1)
    graph.getNode(2).edges.get(4)._2.get(0) should be(1)
    graph.getNode(3).edges.size should be(1)
    graph.getNode(3).edges.get(1)._1.size should be(1)
    graph.getNode(3).edges.get(1)._1.get(0) should be(1)
    graph.getNode(3).edges.get(1)._2.size should be(0)
    graph.getNode(4).edges.size should be(1)
    graph.getNode(4).edges.get(2)._1.size should be(1)
    graph.getNode(4).edges.get(2)._1.get(0) should be(1)
    graph.getNode(4).edges.get(2)._2.size should be(0)
    graph.getNode(5).edges.size should be(1)
    graph.getNode(5).edges.get(3)._1.size should be(0)
    graph.getNode(5).edges.get(3)._2.size should be(1)
    graph.getNode(5).edges.get(3)._2.get(0) should be(1)
    graph.getNode(6).edges.size should be(1)
    graph.getNode(6).edges.get(1)._1.size should be(0)
    graph.getNode(6).edges.get(1)._2.size should be(1)
    graph.getNode(6).edges.get(1)._2.get(0) should be(1)
    graph.getNode("6").edges.size should be(1)
    graph.getNode("6").edges.get(1)._1.size should be(0)
    graph.getNode("6").edges.get(1)._2.size should be(1)
    graph.getNode("6").edges.get(1)._2.get(0) should be(1)
    graph.hasNode("1") should be(true)
    graph.hasNode("2") should be(true)
    graph.hasNode("3") should be(true)
    graph.hasNode("4") should be(true)
    graph.hasNode("5") should be(true)
    graph.hasNode("6") should be(true)
    graph.hasNode("other") should be(false)
    graph.hasEdge("1") should be(true)
    graph.hasEdge("2") should be(true)
    graph.hasEdge("3") should be(true)
    graph.hasEdge("4") should be(true)
    graph.hasEdge("other") should be(false)
  }

  "loadGraph" should "correctly read in a graph" in {
    val graph = new GraphOnDisk(graphDir, outputter, fileUtil)
    testGraphIsCorrect(graph)
  }

  "loadGraphFromBinaryFile" should "correctly load the graph" in {
    // We're going to test both reading _and_ writing here, by starting with a graph as loaded
    // above, writing it to a binary file, then reading it, and testing whether the entries are
    // correct.
    val graph = new GraphOnDisk(graphDir, outputter, fileUtil)
    val realFileUtil = new FileUtil
    realFileUtil.mkdirs(graphDir)
    realFileUtil.writeContentsToFile(nodeDictFile, nodeDictFileContents)
    realFileUtil.writeContentsToFile(edgeDictFile, edgeDictFileContents)
    graph.writeToBinaryFile(graphDir + "edges.dat", realFileUtil)
    val graphFromBinaryFile = new GraphOnDisk(graphDir, outputter, realFileUtil)
    testGraphIsCorrect(graphFromBinaryFile)
    realFileUtil.deleteDirectory(graphDir)
  }
}
