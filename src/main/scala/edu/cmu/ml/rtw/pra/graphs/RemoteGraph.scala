package edu.cmu.ml.rtw.pra.graphs

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.JsonHelper
import edu.cmu.ml.rtw.users.matt.util.SpecFileReader

import scala.collection.concurrent
import scala.collection.mutable

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket

import org.json4s.JNothing

object RemoteGraph {
  val DEFAULT_PORT = 9876
}

class RemoteGraph(val hostname: String, val port: Int) extends Graph {
  lazy val socket = new Socket(hostname, port)
  lazy val out = new ObjectOutputStream(socket.getOutputStream)
  lazy val in = new ObjectInputStream(socket.getInputStream)

  def close() = socket.close()

  val nodeNames = new concurrent.TrieMap[Int, String]
  val nodeIds = new concurrent.TrieMap[String, Int]
  val nodes = new concurrent.TrieMap[Int, Node]

  val edgeNames = new concurrent.TrieMap[Int, String]
  val edgeIds = new concurrent.TrieMap[String, Int]

  lazy val (numNodes, numEdgeTypes) = {
    out.writeObject(GetGraphStats)
    val result = in.readObject().asInstanceOf[StatsResponse]
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
    out.writeObject(GetNodeById(id))
    val result = in.readObject().asInstanceOf[NodeResponse]
    updateNodeCache(result.node, id, result.name)
    (result.node, result.name)
  }

  private def getNodeFromServer(name: String): (Node, Int) = {
    out.writeObject(GetNodeByName(name))
    val result = in.readObject().asInstanceOf[NodeResponse]
    updateNodeCache(result.node, result.id, name)
    (result.node, result.id)
  }

  private def getEdgeFromServer(id: Int): (Int, String) = {
    out.writeObject(GetEdgeById(id))
    val result = in.readObject().asInstanceOf[EdgeResponse]
    updateEdgeCache(id, result.name)
    (id, result.name)
  }

  private def getEdgeFromServer(name: String): (Int, String) = {
    out.writeObject(GetEdgeByName(name))
    val result = in.readObject().asInstanceOf[EdgeResponse]
    updateEdgeCache(result.id, name)
    (result.id, name)
  }

  // We don't need to worry about checking the return values in either of these two methods,
  // because if another thread updated the cache, it will have put the same value in.  We assume
  // that the graph server we're talking to has an immutable graph.
  private def updateNodeCache(node: Node, id: Int, name: String) {
    nodeNames.putIfAbsent(id, name)
    nodeIds.putIfAbsent(name, id)
    nodes.putIfAbsent(id, node)
  }

  private def updateEdgeCache(id: Int, name: String) {
    edgeNames.putIfAbsent(id, name)
    edgeIds.putIfAbsent(name, id)
  }
}

sealed trait GraphMessage
final case class GetNodeById(id: Int) extends GraphMessage
final case class GetNodeByName(name: String) extends GraphMessage
final case class GetEdgeById(id: Int) extends GraphMessage
final case class GetEdgeByName(name: String) extends GraphMessage
final case object GetGraphStats extends GraphMessage

sealed trait GraphResponse
final case class NodeResponse(id: Int, name: String, node: Node) extends GraphResponse
final case class EdgeResponse(id: Int, name: String) extends GraphResponse
final case class StatsResponse(numNodes: Int, numEdgeTypes: Int) extends GraphResponse


class RemoteGraphServer(graph: Graph, port: Int) extends Thread {

  val serverSocket = new ServerSocket(port)

  override def run() {
    while (true) {
      try {
        new Handler(serverSocket.accept()).start()
      } catch {
        case e: InterruptedException => {
          println("Closing the server socket")
          serverSocket.close()
          throw e
        }
        case e: java.net.SocketException => {
          println("Socket closed!")
          return
        }
      }
    }
  }

  def quit() {
    println("Closing the socket")
    serverSocket.close()
  }

  class Handler(socket: Socket) extends Thread {
    val out = new ObjectOutputStream(socket.getOutputStream)
    val in = new ObjectInputStream(socket.getInputStream)

    override def run() {
      while (!socket.isClosed) {
        try {
          val message = in.readObject().asInstanceOf[GraphMessage]
          message match {
            case GetNodeById(id) => {
              val node = graph.getNode(id)
              val nodeName = graph.getNodeName(id)
              out.writeObject(NodeResponse(id, nodeName, node))
            }
            case GetNodeByName(name) => {
              val node = graph.getNode(name)
              val id = graph.getNodeIndex(name)
              out.writeObject(NodeResponse(id, name, node))
            }
            case GetEdgeById(id) => {
              val name = graph.getEdgeName(id)
              out.writeObject(EdgeResponse(id, name))
            }
            case GetEdgeByName(name) => {
              val id = graph.getEdgeIndex(name)
              out.writeObject(EdgeResponse(id, name))
            }
            case GetGraphStats => {
              out.writeObject(StatsResponse(graph.getNumNodes(), graph.getNumEdgeTypes()))
            }
          }
        } catch {
          case e: InterruptedException => {
            println("Closing the handler socket")
            socket.close()
          }
          case e: java.io.EOFException => {
            println("Handler socket closed remotely")
            socket.close()
          }
        }
      }
    }
  }
}

object RunRemoteGraphServer {
  var fileUtil = new FileUtil

  def main(args: Array[String]) {
    import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
    val specFile = args(0)
    val praBase = "/dev/null"
    val params = new SpecFileReader("/dev/null", fileUtil).readSpecFile(specFile)
    val paramKeys = Seq("type", "port", "graph")
    JsonHelper.ensureNoExtras(params, "operation", paramKeys)
    val port = JsonHelper.extractWithDefault(params, "port", RemoteGraph.DEFAULT_PORT)

    val outputter = new Outputter(JNothing, praBase, "running graph")
    val graph = Graph.create(params \ "graph", praBase, outputter, fileUtil).get
    val server = new RemoteGraphServer(graph, port)
    println(s"Starting graph server on port $port")
    server.start()
    try {
      println("Hit enter to stop the server...")
      val in = scala.io.StdIn.readLine()
    } finally {
      println("Quitting")
      server.quit()
    }
  }
}
