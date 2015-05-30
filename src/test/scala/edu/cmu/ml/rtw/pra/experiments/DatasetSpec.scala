package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfig
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

  val graphFilename = "/graph file"
  val graphFileContents = "1\t2\t1\n" +
      "1\t3\t1\n" +
      "1\t4\t2\n" +
      "2\t1\t4\n" +
      "5\t1\t3\n" +
      "6\t1\t1\n"

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
  fileUtil.addFileToBeRead(graphFilename, graphFileContents)
  fileUtil.addFileToBeRead(instanceGraphFilename, instanceGraphFileContents)
  fileUtil.addFileToBeRead(badDatasetFilename, badDatasetFile)

  "fromFile" should "crash on bad third column" in {
    TestUtil.expectError(classOf[RuntimeException], new Function() {
      override def call() {
        Dataset.fromFile(badDatasetFilename, new PraConfig.Builder().noChecks().build(), fileUtil)
      }
    })
  }

  "loadGraph" should "correctly read in a graph" in {
    val config = new PraConfig.Builder()
      .setUnallowedEdges(Seq(1:Integer).asJava).setGraph(graphFilename).noChecks().build()
    val graph = new Dataset(Seq(), config, None, fileUtil).loadGraph(graphFilename, 7)
    graph.size should be(7)
    graph.getNode(0).edges should be(Map())
    graph.getNode(1).edges.size should be(4)
    graph.getNode(1).edges(1)._1.size should be(1)
    graph.getNode(1).edges(1)._1(0) should be(6)
    graph.getNode(1).edges(1)._2.size should be(2)
    graph.getNode(1).edges(1)._2(0) should be(2)
    graph.getNode(1).edges(1)._2(1) should be(3)
    graph.getNode(1).edges(2)._1.size should be(0)
    graph.getNode(1).edges(2)._2.size should be(1)
    graph.getNode(1).edges(2)._2(0) should be(4)
    graph.getNode(1).edges(3)._1.size should be(1)
    graph.getNode(1).edges(3)._1(0) should be(5)
    graph.getNode(1).edges(3)._2.size should be(0)
    graph.getNode(1).edges(4)._1.size should be(1)
    graph.getNode(1).edges(4)._1(0) should be(2)
    graph.getNode(1).edges(4)._2.size should be(0)
    graph.getNode(2).edges.size should be(2)
    graph.getNode(2).edges(1)._1.size should be(1)
    graph.getNode(2).edges(1)._1(0) should be(1)
    graph.getNode(2).edges(1)._2.size should be(0)
    graph.getNode(2).edges(4)._1.size should be(0)
    graph.getNode(2).edges(4)._2.size should be(1)
    graph.getNode(2).edges(4)._2(0) should be(1)
    graph.getNode(3).edges.size should be(1)
    graph.getNode(3).edges(1)._1.size should be(1)
    graph.getNode(3).edges(1)._1(0) should be(1)
    graph.getNode(3).edges(1)._2.size should be(0)
    graph.getNode(4).edges.size should be(1)
    graph.getNode(4).edges(2)._1.size should be(1)
    graph.getNode(4).edges(2)._1(0) should be(1)
    graph.getNode(4).edges(2)._2.size should be(0)
    graph.getNode(5).edges.size should be(1)
    graph.getNode(5).edges(3)._1.size should be(0)
    graph.getNode(5).edges(3)._2.size should be(1)
    graph.getNode(5).edges(3)._2(0) should be(1)
    graph.getNode(6).edges.size should be(1)
    graph.getNode(6).edges(1)._1.size should be(0)
    graph.getNode(6).edges(1)._2.size should be(1)
    graph.getNode(6).edges(1)._2(0) should be(1)
  }

  "fromFile" should "correctly read an instance-graph dataset file" in {
    val config = new PraConfig.Builder()
      .setUnallowedEdges(Seq(1:Integer).asJava).noChecks().build()
    val dataset = Dataset.fromFile(instanceGraphFilename, config, fileUtil)
    val graph1 = dataset.getGraphForInstance(0)

    // There are three nodes in the graph, but four entries in the array, because we use the
    // dictionary index, and there's never any node at 0.  I suppose I could change the indices...
    // But that would be a bit confusing, because you'd have to keep track of when you shift the
    // index and when you don't.
    graph1.size should be(4)
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

    val graph2 = dataset.getGraphForInstance(1)
    graph2.size should be(5)
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
