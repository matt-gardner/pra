package edu.cmu.ml.rtw.pra.features

import java.util.Arrays
import java.util.Random

import edu.cmu.ml.rtw.pra.graphs.Graph

abstract class BaseEdgeSequencePathType(
  val edgeTypes: Array[Int],
  val reverse: Array[Boolean]
) extends PathType {
  val numHops = edgeTypes.length

  // The +1 here is because we need an iteration for each node, not for each edge
  override def recommendedIters() = numHops + 1

  override def isLastHop(hopNum: Int) = hopNum == numHops - 1

  override def nextHop(
    hopNum: Int,
    sourceVertex: Int,
    currentVertex: Vertex,
    random: Random,
    edgeExcluder: EdgeExcluder,
    cache: PathTypeVertexCache
  ): Int = {
    if (hopNum >= numHops) {
      return -1
    }
    val edgeType = getNextEdgeType(hopNum, currentVertex, random, cache)
    val isReverse = reverse(hopNum)
    val possibleHops = currentVertex.getPossibleNodes(edgeType, isReverse)
    if (possibleHops == null) {
      return -1
    }
    val nextVertex = possibleHops(random.nextInt(possibleHops.length))
    if (edgeExcluder.shouldExcludeEdge(sourceVertex, nextVertex, currentVertex, edgeType)) {
      return -1
    }
    return nextVertex
  }

  def getEdgeTypes() = edgeTypes.clone()

  def getReverse() = reverse.clone()

  protected def getNextEdgeType(
    hopNum: Int,
    vertex: Vertex,
    random: Random,
    cache: PathTypeVertexCache): Int

  override def encodeAsString() = stringDescription(null, Map())

  override def encodeAsHumanReadableString(graph: Graph, edgeMap: Map[Int, String] = Map()) = {
    stringDescription(graph, edgeMap)
  }

  private def stringDescription(graph: Graph, edgeMap: Map[Int, String]): String = {
    val builder = new StringBuilder()
    builder.append("-")
    for (i <- 0 until numHops) {
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
      builder.append("-")
    }
    return builder.toString()
  }

  override def toString() = encodeAsString()

  override def hashCode() = {
    val prime = 31
    var result = 1
    result = prime * result + numHops
    result = prime * result + Arrays.hashCode(edgeTypes)
    result = prime * result + Arrays.hashCode(reverse)
    result
  }

  override def equals(other: Any) = {
    other match {
      case that: BaseEdgeSequencePathType => {
        numHops == that.numHops &&
        Arrays.equals(edgeTypes, that.edgeTypes) &&
        Arrays.equals(reverse, that.reverse)
      }
      case _ => {
        false
      }
    }
  }
}
