package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

import akka.typed.ActorSystem
import akka.typed.Props

class RemoteGraphSpec extends FlatSpecLike with Matchers {

  val outputter = Outputter.justLogger
  val graphFilename = "/graph/graph_chi/edges.tsv"
  val graphFileContents = "1\t2\t1\n" +
      "1\t3\t1\n" +
      "1\t4\t2\n" +
      "2\t1\t4\n" +
      "5\t1\t3\n" +
      "6\t1\t1\n"

  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(graphFilename, graphFileContents)
  fileUtil.addFileToBeRead("/graph/node_dict.tsv", "1\t1\n2\t2\n3\t3\n4\t4\n5\t5\n6\t6\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv", "1\t1\n2\t2\n3\t3\n4\t4\n")
  val graphOnDisk = new GraphOnDisk("/graph/", outputter, fileUtil)

  "remote graph" should "actually work with a 'local' remote graph" in {
    val remoteGraphServer = new RemoteGraphServer(graphOnDisk)
    val system = ActorSystem("system", Props(remoteGraphServer.handler))
    val graph = new RemoteGraph(system)
    graph.getNumNodes() should be(7)
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

