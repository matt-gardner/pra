package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

class DatasetSpec extends FlatSpecLike with Matchers {

  val datasetFile = "node1\tnode2\t1\n" + "node1\tnode3\t-1\n"

  val badDatasetFile = datasetFile + "node1\tnode4\tnode5\n"
  val badDatasetFilename = "/bad/filename"

  val instanceGraphFilename = "/instance graph file"
  val instanceGraphFileContents =
    // the (positive) instance
    "node1\tnode2\t1\t" +
      // the graph for the instance
      "node1^,^node3^,^rel1 ### node3^,^node2^,^rel2\n" +
    // and for good measure, a negative instance
    "node3\tnode4\t-1\t" +
      // and the graph for the instance
      "node3^,^node5^,^rel1 ### node4^,^node6^,^rel3\n"


  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(instanceGraphFilename, instanceGraphFileContents)
  fileUtil.addFileToBeRead(badDatasetFilename, badDatasetFile)
  fileUtil.addFileToBeRead("/graph/node_dict.tsv", "1\tnode1\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv", "1\trel1\n")
  val graphOnDisk = new GraphOnDisk("/graph/", fileUtil)

  "fromFile" should "crash on bad third column" in {
    TestUtil.expectError(classOf[IllegalStateException], "not formatted correctly", new Function() {
      override def call() {
        Dataset.fromFile(badDatasetFilename, Some(graphOnDisk), fileUtil)
      }
    })
  }

  "fromFile" should "correctly read an instance-graph dataset file" in {
    val dataset = Dataset.fromFile(instanceGraphFilename, None, fileUtil)
    val graph1 = dataset.instances(0).graph

    // There are three nodes in the graph, but four entries in the array, because we use the
    // dictionary index, and there's never any node at 0.  I suppose I could change the indices...
    // But that would be a bit confusing, because you'd have to keep track of when you shift the
    // index and when you don't.
    graph1.entries.size should be(4)
    graph1.getNode(0).edges should be(Map())
    graph1.getNode("node1").edges.size should be(1)
    graph1.getNode("node1").getEdges("rel1")._1.size should be(0)
    graph1.getNode("node1").getEdges("rel1")._2.size should be(1)
    graph1.getNode("node1").getEdges("rel1")._2(0) should be(graph1.nodeDict.getIndex("node3"))
    graph1.getNode("node2").edges.size should be(1)
    graph1.getNode("node2").getEdges("rel2")._1.size should be(1)
    graph1.getNode("node2").getEdges("rel2")._1(0) should be(graph1.nodeDict.getIndex("node3"))
    graph1.getNode("node2").getEdges("rel2")._2.size should be(0)
    graph1.getNode("node3").edges.size should be(2)
    graph1.getNode("node3").getEdges("rel1")._1.size should be(1)
    graph1.getNode("node3").getEdges("rel1")._1(0) should be(graph1.nodeDict.getIndex("node1"))
    graph1.getNode("node3").getEdges("rel1")._2.size should be(0)
    graph1.getNode("node3").getEdges("rel2")._1.size should be(0)
    graph1.getNode("node3").getEdges("rel2")._2.size should be(1)
    graph1.getNode("node3").getEdges("rel2")._2(0) should be(graph1.nodeDict.getIndex("node2"))

    val graph2 = dataset.instances(1).graph
    graph2.entries.size should be(5)
    graph2.getNode(0).edges should be(Map())
    graph2.getNode("node3").edges.size should be(1)
    graph2.getNode("node3").getEdges("rel1")._1.size should be(0)
    graph2.getNode("node3").getEdges("rel1")._2.size should be(1)
    graph2.getNode("node3").getEdges("rel1")._2(0) should be(graph2.nodeDict.getIndex("node5"))
    graph2.getNode("node4").edges.size should be(1)
    graph2.getNode("node4").getEdges("rel3")._1.size should be(0)
    graph2.getNode("node4").getEdges("rel3")._2.size should be(1)
    graph2.getNode("node4").getEdges("rel3")._2(0) should be(graph2.nodeDict.getIndex("node6"))
    graph2.getNode("node5").edges.size should be(1)
    graph2.getNode("node5").getEdges("rel1")._1.size should be(1)
    graph2.getNode("node5").getEdges("rel1")._1(0) should be(graph2.nodeDict.getIndex("node3"))
    graph2.getNode("node5").getEdges("rel1")._2.size should be(0)
    graph2.getNode("node6").edges.size should be(1)
    graph2.getNode("node6").getEdges("rel3")._1.size should be(1)
    graph2.getNode("node6").getEdges("rel3")._1(0) should be(graph2.nodeDict.getIndex("node4"))
    graph2.getNode("node6").getEdges("rel3")._2.size should be(0)
  }
}
