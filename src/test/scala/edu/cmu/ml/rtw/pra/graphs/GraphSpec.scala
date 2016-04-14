package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._

class GraphSpec extends FlatSpecLike with Matchers {

  val praBase = "/praBase/"
  val graphBase = praBase + "/graphs/"
  val outputter = Outputter.justLogger

  val graphFilename = "/graph/graph_chi/edges.tsv"
  val graphFileContents = "1\t2\t1\n" +
      "1\t3\t1\n" +
      "1\t4\t2\n" +
      "2\t1\t4\n" +
      "5\t1\t3\n" +
      "6\t1\t1\n"

  val nodeDictFile = "/graph/node_dict.tsv"
  val nodeDictFileContents = "1\t1\n2\t2\n3\t3\n4\t4\n5\t5\n6\t6\n"
  val edgeDictFile = "/graph/edge_dict.tsv"
  val edgeDictFileContents = "1\t1\n2\t2\n3\t3\n4\t4\n"

  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(graphFilename, graphFileContents)
  fileUtil.addFileToBeRead(nodeDictFile, nodeDictFileContents)
  fileUtil.addFileToBeRead(edgeDictFile, edgeDictFileContents)

  "Graph.create" should "return None with empty params" in {
    val params = JNothing
    val graph = Graph.create(params, graphBase, outputter, fileUtil)
    graph should be(None)
  }

  it should "return a GraphOnDisk when given a relative path" in {
    val params = JString("graph_name")
    val graphOption = Graph.create(params, graphBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [GraphOnDisk]
    // I'm not going to worry about the extra / for now, but that should probably be dealt with at
    // some point.
    graph.asInstanceOf[GraphOnDisk].graphDir should be("/praBase//graphs/graph_name/")
  }

  it should "return a GraphOnDisk when given an absolute path" in {
    val pathToGraph = "/path/to/graph/"
    val params = JString(pathToGraph)
    val graphOption = Graph.create(params, graphBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [GraphOnDisk]
    graph.asInstanceOf[GraphOnDisk].graphDir should be(pathToGraph)
  }

  it should "return a GraphOnDisk when given a name" in {
    val graphName = "graph_name"
    val params: JValue = ("name" -> graphName)
    val graphOption = Graph.create(params, graphBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [GraphOnDisk]
    graph.asInstanceOf[GraphOnDisk].graphDir should be(s"/praBase//graphs/$graphName/")
  }

  it should "return a RemoteGraph when specified" in {
    val graphName = "graph_name"
    val port = 32
    val hostname = "random hostname"
    val params: JValue = ("type" -> "remote") ~ ("hostname" -> hostname) ~ ("port" -> port)
    val graphOption = Graph.create(params, graphBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [RemoteGraph]
    graph.asInstanceOf[RemoteGraph].hostname should be(hostname)
    graph.asInstanceOf[RemoteGraph].port should be(port)
  }

  "getAllTriples" should "return correct triples" in {
    val graph = new GraphOnDisk("/graph/", outputter, fileUtil)
    val triples = graph.getAllTriples()
    // Triple format is (source, relation, target), while in the graph file above it's (source,
    // target, relation).
    triples.size should be(6)
    triples should contain((1, 1, 2))
    triples should contain((1, 1, 3))
    triples should contain((1, 2, 4))
    triples should contain((2, 4, 1))
    triples should contain((5, 3, 1))
    triples should contain((6, 1, 1))
  }
}
