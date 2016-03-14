package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

import gnu.trove.{TIntObjectHashMap => TMap}

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

  // TODO(matt): I don't think I've specified what should happen if you query for a node or edge
  // without checking that it's present.  Should I expect a null pointer exception, or do something
  // else?
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

  "remote graph" should "actually work with a 'local' remote graph" in {
    val port = 9877
    val remoteGraphServer = new RemoteGraphServer(graphOnDisk, port)
    remoteGraphServer.start()
    val graph = new RemoteGraph("localhost", port, 2)
    testGraphIsCorrect(graph)
    graph.close()
    remoteGraphServer.quit()
  }

  it should "work when the server is in another process" in {
    val port = 9877
    val thread = new Thread() {
      override def run() {
        RunRemoteGraphServer.fileUtil = fileUtil
        RunRemoteGraphServer.main(Array("--graph", "/graph/", "--port", port.toString))
      }
    }
    thread.start()
    Thread.sleep(100)
    val graph = new RemoteGraph("localhost", port, 2)
    testGraphIsCorrect(graph)
    graph.close()
    thread.interrupt()
  }

}

