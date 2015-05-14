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


  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(graphFilename, graphFileContents)
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
}
