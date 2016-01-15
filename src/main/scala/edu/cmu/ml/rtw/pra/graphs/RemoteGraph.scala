package edu.cmu.ml.rtw.pra.graphs

import scala.collection.concurrent
import scala.collection.mutable

import akka.util.Timeout
import akka.typed._
import akka.typed.ScalaDSL._
import akka.typed.AskPattern._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await

class RemoteGraph(system: ActorSystem[GraphMessage]) extends Graph {
  import scala.language.postfixOps
  val timeout = 1 seconds
  implicit val t = Timeout(timeout)

  val nodeNames = new concurrent.TrieMap[Int, String]
  val nodeIds = new concurrent.TrieMap[String, Int]
  val nodes = new concurrent.TrieMap[Int, Node]

  val edgeNames = new concurrent.TrieMap[Int, String]
  val edgeIds = new concurrent.TrieMap[String, Int]

  lazy val (numNodes, numEdgeTypes) = {
    val response = system ? { f: ActorRef[StatsResponse] => GetGraphStats(f) }
    val result = Await.result(response, timeout)
    (result.numNodes, result.numEdgeTypes)
  }

  override def entries = throw new RuntimeException("cannot get entries array on remote graph")

  override def getNode(i: Int): Node = {
    nodes.get(i) match {
      case Some(node) => node
      case None => {
        getNodeFromServer(i)._1
      }
    }
  }

  override def getNode(name: String): Node = {
    val id = nodeIds.get(name) match {
      case Some(index) => index
      case None => getNodeFromServer(name)._2
    }
    nodes(id)
  }

  override def getNodeName(i: Int): String = {
    nodeNames.get(i) match {
      case Some(name) => name
      case None => getNodeFromServer(i)._2
    }
  }

  override def getNodeIndex(name: String): Int = {
    nodeIds.get(name) match {
      case Some(id) => id
      case None => getNodeFromServer(name)._2
    }
  }

  override def getNumNodes(): Int = numNodes

  override def getEdgeName(i: Int): String = {
    edgeNames.get(i) match {
      case Some(name) => name
      case None => getEdgeFromServer(i)._2
    }
  }

  override def getEdgeIndex(name: String): Int = {
    edgeIds.get(name) match {
      case Some(id) => id
      case None => getEdgeFromServer(name)._1
    }
  }

  override def getNumEdgeTypes(): Int = numEdgeTypes

  private def getNodeFromServer(id: Int): (Node, String) = {
    val response = system ? { f: ActorRef[NodeResponse] => GetNodeById(id, f) }
    val result = Await.result(response, timeout)
    nodeNames.putIfAbsent(id, result.name)
    nodeIds.putIfAbsent(result.name, id)
    nodes.putIfAbsent(id, result.node)
    (result.node, result.name)
  }

  private def getNodeFromServer(name: String): (Node, Int) = {
    val response = system ? { f: ActorRef[NodeResponse] => GetNodeByName(name, f) }
    val result = Await.result(response, timeout)
    nodeNames.putIfAbsent(result.id, name)
    nodeIds.putIfAbsent(name, result.id)
    nodes.putIfAbsent(result.id, result.node)
    (result.node, result.id)
  }

  private def getEdgeFromServer(id: Int): (Int, String) = {
    val response = system ? { f: ActorRef[EdgeResponse] => GetEdgeById(id, f) }
    val result = Await.result(response, timeout)
    edgeNames.putIfAbsent(id, result.name)
    edgeIds.putIfAbsent(result.name, id)
    (id, result.name)
  }

  private def getEdgeFromServer(name: String): (Int, String) = {
    val response = system ? { f: ActorRef[EdgeResponse] => GetEdgeByName(name, f) }
    val result = Await.result(response, timeout)
    edgeNames.putIfAbsent(result.id, name)
    edgeIds.putIfAbsent(name, result.id)
    (result.id, name)
  }
}

sealed trait GraphMessage
final case class GetNodeById(id: Int, replyTo: ActorRef[NodeResponse]) extends GraphMessage
final case class GetNodeByName(name: String, replyTo: ActorRef[NodeResponse]) extends GraphMessage
final case class GetEdgeById(id: Int, replyTo: ActorRef[EdgeResponse]) extends GraphMessage
final case class GetEdgeByName(name: String, replyTo: ActorRef[EdgeResponse]) extends GraphMessage
final case class GetGraphStats(replyTo: ActorRef[StatsResponse]) extends GraphMessage

sealed trait GraphResponse
final case class NodeResponse(id: Int, name: String, node: Node) extends GraphResponse
final case class EdgeResponse(id: Int, name: String) extends GraphResponse
final case class StatsResponse(numNodes: Int, numEdgeTypes: Int) extends GraphResponse


class RemoteGraphServer(graph: Graph) {
  val handler = Static[GraphMessage] {
    case GetNodeById(id, replyTo) => {
      val node = graph.getNode(id)
      val nodeName = graph.getNodeName(id)
      replyTo ! NodeResponse(id, nodeName, node)
    }
    case GetNodeByName(name, replyTo) => {
      val node = graph.getNode(name)
      val id = graph.getNodeIndex(name)
      replyTo ! NodeResponse(id, name, node)
    }
    case GetEdgeById(id, replyTo) => {
      val name = graph.getEdgeName(id)
      replyTo ! EdgeResponse(id, name)
    }
    case GetEdgeByName(name, replyTo) => {
      val id = graph.getEdgeIndex(name)
      replyTo ! EdgeResponse(id, name)
    }
    case GetGraphStats(replyTo) => {
      replyTo ! StatsResponse(graph.getNumNodes(), graph.getNumEdgeTypes())
    }
  }
}
