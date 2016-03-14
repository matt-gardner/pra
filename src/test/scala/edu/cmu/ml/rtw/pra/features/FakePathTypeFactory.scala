package edu.cmu.ml.rtw.pra.features

import java.util.Random

import edu.cmu.ml.rtw.pra.graphs.Graph

class FakePathTypeFactory extends PathTypeFactory {

  val pathTypes = Array[PathType](
    new FakePathType("fake path type 1"),
    new FakePathType("fake path type 2")
  )

  override def encode(path: Path) = pathTypes

  override def fromString(string: String) = new FakePathType(string)
  override def fromHumanReadableString(string: String) = new FakePathType(string)

  override def emptyPathType() = new FakePathType("NULL")

  override def concatenatePathTypes(pathToSource: PathType, pathFromTarget: PathType) = {
    new FakePathType(pathToSource.encodeAsString() + pathFromTarget.encodeAsString())
  }

  override def addToPathType(pathToSource: PathType, relation: Int, node: Int, reverse: Boolean) = {
    throw new UnsupportedOperationException()
  }
}

class FakePathType(val description: String) extends PathType {

  val numHops = 3
  var nextVertex: Int = 0

  def setNextVertex(n: Int) { nextVertex = n }

  override def recommendedIters() = numHops

  override def isLastHop(hopNum: Int) = hopNum == numHops - 1

  override def encodeAsString() = description

  override def encodeAsHumanReadableString(graph: Graph, edgeMap: Map[Int, String] = Map()) = {
    description
  }

  override def cacheVertexInformation(vertex: Vertex, hopNum: Int) = null

  override def nextHop(
    hopNum: Int,
    sourceVertex: Int,
    vertex: Vertex,
    random: Random,
    edgeExcluder: EdgeExcluder,
    cache: PathTypeVertexCache) = nextVertex

  override def equals(obj: Any): Boolean = {
    if (this == obj)
      return true
    if (obj == null)
      return false
    if (getClass() != obj.getClass())
      return false
    val other = obj.asInstanceOf[FakePathType]
    if (numHops != other.numHops)
      return false
    if (!description.equals(other.description))
      return false
    return true
  }

  override def hashCode() = {
    val prime = 31
    var result = 1
    result = prime * result + numHops
    result = prime * result + description.hashCode()
    result
  }
}
