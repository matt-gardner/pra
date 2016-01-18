package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._

class GraphSpec extends FlatSpecLike with Matchers {

  val praBase = "/praBase/"
  val fileUtil = new FakeFileUtil
  val outputter = Outputter.justLogger

  "Graph.create" should "return None with empty params" in {
    val params = JNothing
    val graph = Graph.create(params, praBase, outputter, fileUtil)
    graph should be(None)
  }

  it should "return a GraphOnDisk when given a relative path" in {
    val params = JString("graph_name")
    val graphOption = Graph.create(params, praBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [GraphOnDisk]
    // I'm not going to worry about the extra / for now, but that should probably be dealt with at
    // some point.
    graph.asInstanceOf[GraphOnDisk].graphDir should be("/praBase//graphs/graph_name/")
  }

  it should "return a GraphOnDisk when given an absolute path" in {
    val pathToGraph = "/path/to/graph/"
    val params = JString(pathToGraph)
    val graphOption = Graph.create(params, praBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [GraphOnDisk]
    graph.asInstanceOf[GraphOnDisk].graphDir should be(pathToGraph)
  }

  it should "return a GraphOnDisk when given a name" in {
    val graphName = "graph_name"
    val params: JValue = ("name" -> graphName)
    val graphOption = Graph.create(params, praBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [GraphOnDisk]
    graph.asInstanceOf[GraphOnDisk].graphDir should be(s"/praBase//graphs/$graphName/")
  }

  it should "return a RemoteGraph when specified" in {
    val graphName = "graph_name"
    val port = 32
    val hostname = "random hostname"
    val params: JValue = ("type" -> "remote") ~ ("hostname" -> hostname) ~ ("port" -> port)
    val graphOption = Graph.create(params, praBase, outputter, fileUtil)
    val graph = graphOption.get
    graph shouldBe a [RemoteGraph]
    graph.asInstanceOf[RemoteGraph].hostname should be(hostname)
    graph.asInstanceOf[RemoteGraph].port should be(port)
  }
}
