package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.graphs.Graph
import com.mattg.util.JsonHelper

import java.util.Random

import org.json4s._
import org.json4s.native.JsonMethods._

class LexicalizedPathType(
    val edgeTypes: Array[Int],
    val nodes: Array[Int],
    val reverse: Array[Boolean],
    val params: JValue) extends PathType {
  implicit val formats = DefaultFormats

  val numHops = edgeTypes.size

  val removeColon = JsonHelper.extractWithDefault(params, "remove colon", "filter")
  val removeColonErrorMessage = "With remove colon: filter, second column must be KEEP or REMOVE"

  override def recommendedIters() = {
    throw new UnsupportedOperationException("LexicalizedPathTypes can't be followed at the moment")
  }

  override def isLastHop(hopNum: Int) = {
    throw new UnsupportedOperationException("LexicalizedPathTypes can't be followed at the moment")
  }

  override def nextHop(
      hopNum: Int,
      sourceVertex: Int,
      currentVertex: Vertex,
      random: Random,
      edgeExcluder: EdgeExcluder,
      cache: PathTypeVertexCache) = {
    throw new UnsupportedOperationException("LexicalizedPathTypes can't be followed at the moment")
  }

  override def cacheVertexInformation(vertex: Vertex, hopNum: Int) = {
    throw new UnsupportedOperationException("LexicalizedPathTypes can't be followed at the moment")
  }

  override def encodeAsString() = stringDescription(null, Map())

  override def encodeAsHumanReadableString(graph: Graph, edgeMap: Map[Int, String] = Map()) = {
    stringDescription(graph, edgeMap)
  }

  def encodeAsHumanReadableStringWithoutNodes(graph: Graph, edgeMap: Map[Int, String] = Map()) = {
    val builder = new StringBuilder()
    for (i <- 0 until numHops) {
      builder.append("-")
      if (reverse(i)) {
        builder.append("_")
      }
      val edgeName = edgeMap.get(edgeTypes(i)) match {
        case Some(name) => name
        case None => graph.getEdgeName(edgeTypes(i))
      }
      builder.append(edgeName)
    }
    builder.append("-")
    builder.toString()
  }

  def stringDescription(graph: Graph, edgeMap: Map[Int, String]) = {
    val builder = new StringBuilder()
    for (i <- 0 until numHops) {
      builder.append("-")
      if (reverse(i)) {
        builder.append("_")
      }
      if (graph == null) {
        builder.append(edgeTypes(i))
      } else {
        val edgeName = edgeMap.get(edgeTypes(i)) match {
          case Some(name) => name
          case None => graph.getEdgeName(edgeTypes(i))
        }
        builder.append(edgeName)
      }
      builder.append("->")
      if (i < nodes.size) {
        val nodeStr = if (graph == null) nodes(i).toString else graph.getNodeName(nodes(i))
        removeColon match {
          case "no" => builder.append(nodeStr)
          case "yes" => builder.append(nodeStr.split(":").last)
          case "filter" => {
            if (nodeStr.contains(":")) {
              val parts = nodeStr.split(":")
              parts(1) match {
                case "KEEP" => builder.append(parts.last)
                case "REMOVE" => { }
                case _ => throw new IllegalStateException(removeColonErrorMessage)
              }
            } else {
              builder.append(nodeStr)
            }
          }
        }
      }
    }
    builder.toString()
  }

  override def toString() = encodeAsString()

  private def hashArray[A <: Any](array: Array[A]) = {
    val prime = 37
    var result = 1
    for (element <- array) {
      result = prime * result + element.hashCode
    }
    result
  }

  override def hashCode() = {
    val prime = 31
    var result = 1
    result = prime * result + hashArray(edgeTypes)
    result = prime * result + hashArray(nodes)
    result = prime * result + hashArray(reverse)
    result
  }

  override def equals(other: Any) = other match {
    case that: LexicalizedPathType => {
      if (!edgeTypes.sameElements(that.edgeTypes)) {
        false
      } else if (!nodes.sameElements(that.nodes)) {
        false
      } else if (!reverse.sameElements(that.reverse)) {
        false
      } else {
        true
      }
    }
    case _ => false
  }
}

class LexicalizedPathTypeFactory(params: JValue, graph: Graph) extends PathTypeFactory {

  private def parseRelation(relation: String, graph: Option[Graph]) = {
    val (rel, reverse) = relation(0) match {
      case '_' => (relation.substring(1), true)
      case _ => (relation, false)
    }
    graph match {
      case None => (rel.toInt, reverse)
      case Some(g) => (g.getEdgeIndex(rel), reverse)
    }
  }

  private def parseNode(node: String, graph: Option[Graph]) = {
    graph match {
      case None => node.toInt
      case Some(g) => g.getNodeIndex(node)
    }
  }

  override def emptyPathType() = {
    new LexicalizedPathType(Array[Int](), Array[Int](), Array[Boolean](), params)
  }

  override def fromString(string: String) = {
    _fromString(string, None)
  }

  override def fromHumanReadableString(string: String) = {
    _fromString(string, Some(graph))
  }

  private def _fromString(string: String, graph: Option[Graph]) = {
    // Description is formatted like -rel1->node2-_rel3->node4.  The strategy we'll use here is to
    // split on the '->' first, which will give us 'node-rel' pairs (plus a rel at the front and a
    // node at the end).  We'll parse through these to get the result.

    // First, though, because these path types can either be lexicalized on the last node or not,
    // we need to handle the case where the string ends in '->' (i.e., is not lexicalized at the
    // end).  We'll do this by adding a dummy variable for those cases, and removing it if we see
    // it.
    val dummyStr = "@DUMMY@"
    val pathString = if (string.endsWith("->")) string + dummyStr else string
    val parts = pathString.substring(1).split("->")
    val edgesWithReverse = parts.zipWithIndex.flatMap(part_idx => {
      val part = part_idx._1
      val index = part_idx._2
      if (index == 0) {
        Seq(parseRelation(part, graph))
      } else if (index == parts.size - 1) {
        Seq()
      } else {
        Seq(parseRelation(part.split("-")(1), graph))
      }
    })
    val edges = edgesWithReverse.map(_._1).toArray
    val reverse = edgesWithReverse.map(_._2).toArray
    val nodes = parts.zipWithIndex.flatMap(part_idx => {
      val part = part_idx._1
      val index = part_idx._2
      if (index == 0) {
        Seq()
      } else if (index == parts.size - 1) {
        if (part == dummyStr) {
          Seq()
        } else {
          Seq(parseNode(part, graph))
        }
      } else {
        Seq(parseNode(part.split("-")(0), graph))
      }
    }).toArray
    new LexicalizedPathType(edges, nodes, reverse, params)
  }

  override def encode(path: Path) = {
    Array(new LexicalizedPathType(path.getEdges, path.getNodes, path.getReverse, params))
  }

  override def addToPathType(pathType: PathType, relation: Int, node: Int, reverse: Boolean) = {
    val path = pathType.asInstanceOf[LexicalizedPathType]
    val edges = (path.edgeTypes.clone.toList :+ relation).toArray
    val nodes = (path.nodes.clone.toList :+ node).toArray
    val reverses = (path.reverse.clone.toList :+ reverse).toArray
    new LexicalizedPathType(edges, nodes, reverses, params)
  }

  override def concatenatePathTypes(pathFromSource: PathType, pathFromTarget: PathType): PathType = {
    val source = pathFromSource.asInstanceOf[LexicalizedPathType]
    val target = pathFromTarget.asInstanceOf[LexicalizedPathType]

    // First, if the path from the target is empty, we'll just return the path from the source,
    // minus the last node.
    if (target.numHops == 0) {
      val edges = source.edgeTypes.clone
      val nodes = source.nodes.clone.dropRight(1)
      val reverse = source.reverse.clone
      return new LexicalizedPathType(edges, nodes, reverse, params)
    }

    // Now, if the path from the source isn't empty, we need to be sure that the last nodes on each
    // path match; otherwise concatenating them is an error.  The code that calls this shouldn't
    // allow this to happen, but we put this check here just in case there's a bug somewhere.
    if (source.numHops > 0 && source.nodes.last != target.nodes.last) {
      throw new IllegalStateException("Cannot concatenate path types if the nodes don't match")
    }

    // We don't keep around the first node in a path in this representation.  That means that if
    // you concatenate two of these paths, you'll get something that has no node on either side of
    // the path.  We can't concatenate such a path type again.
    if (source.edgeTypes.size != source.nodes.size || target.edgeTypes.size != target.nodes.size) {
      throw new IllegalStateException("Cannot concatenate path types that end in an edge")
    }

    // Ok, now we can actually concatenate the path types and return the result.  If the path from
    // the source is empty, this code should still work just fine.
    val totalHops = source.numHops + target.numHops
    val combinedEdgeTypes = new Array[Int](totalHops)
    // Because the path types share a common node, there will be one less node than edge.
    val combinedNodes = new Array[Int](totalHops - 1)
    val combinedReverse = new Array[Boolean](totalHops)
    System.arraycopy(source.edgeTypes, 0, combinedEdgeTypes, 0, source.numHops);
    System.arraycopy(source.nodes, 0, combinedNodes, 0, source.numHops);
    System.arraycopy(source.reverse, 0, combinedReverse, 0, source.numHops);

    var i = target.numHops - 1
    var j = source.numHops
    while (i >= 0) {
      combinedEdgeTypes(j) = target.edgeTypes(i)
      if (i > 0) combinedNodes(j) = target.nodes(i - 1)
      combinedReverse(j) = !target.reverse(i)
      i -= 1
      j += 1
    }
    new LexicalizedPathType(combinedEdgeTypes, combinedNodes, combinedReverse, params)
  }

  def reversePathType(pathType: LexicalizedPathType): LexicalizedPathType = {
    // A bunch of boxing and unboxing here, but oh well.  This isn't called in a place where
    // efficiency is really critical.
    val reversedEdgeTypes = pathType.edgeTypes.toSeq.reverse.toArray
    val reversedNodes = pathType.nodes.toSeq.reverse.toArray
    val reversedReverse = pathType.reverse.toSeq.reverse.map(!_).toArray
    new LexicalizedPathType(reversedEdgeTypes, reversedNodes, reversedReverse, pathType.params)
  }
}
