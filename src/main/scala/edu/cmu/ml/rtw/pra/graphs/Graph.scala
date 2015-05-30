package edu.cmu.ml.rtw.pra.graphs

import scala.collection.mutable

import edu.cmu.ml.rtw.users.matt.util.Dictionary

class GraphBuilder(
    initialSize: Int = -1,
    nodeDict: Dictionary = new Dictionary,
    edgeDict: Dictionary = new Dictionary) {
  type MutableGraphEntry = mutable.HashMap[Int, mutable.HashMap[Boolean, Set[Int]]]
  var entries = new Array[MutableGraphEntry](if (initialSize > 0) initialSize else 100)
  (0 until entries.size).par.foreach(i => { entries(i) = new MutableGraphEntry })
  var maxIndexSeen = -1

  def addEdge(source: String, target: String, relation: String) {
    addEdge(nodeDict.getIndex(source), nodeDict.getIndex(target), edgeDict.getIndex(relation))
  }

  def addEdge(source: Int, target: Int, relation: Int) {
    if (source > maxIndexSeen) maxIndexSeen = source
    if (target > maxIndexSeen) maxIndexSeen = target
    if (source >= entries.size || target >= entries.size) {
      growEntries()
    }
    val sourceMap = entries(source).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
    sourceMap.update(false, sourceMap.getOrElse(false, Set()) + target)
    val targetMap = entries(target).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
    targetMap.update(true, targetMap.getOrElse(true, Set()) + source)
  }

  def growEntries() {
    val oldSize = entries.size
    val newSize = oldSize * 2
    val newEntries = new Array[MutableGraphEntry](newSize)
    Array.copy(entries, 0, newEntries, 0, oldSize)
    (oldSize until newSize).par.foreach(i => { newEntries(i) = new MutableGraphEntry })
    entries = newEntries
  }

  def build(): Graph = {
    // If no initial size was provided, we try to trim the size of the resultant array (this should
    // cut down the graph size by at most a factor of 2).  If we were given an initial graph size,
    // then the caller probably knew how big the graph was, and might query for nodes that we never
    // actually saw edges for, and we'll need an empty node representations for that.
    val finalSize = if (initialSize == -1) maxIndexSeen + 1 else entries.size
    val finalized = new Array[Node](finalSize)
    (0 until finalSize).par.foreach(i => {
      if (entries(i) == null) {
        finalized(i) = new Node(Map(), edgeDict)
      } else {
        finalized(i) = new Node(entries(i).map(entry => {
          val relation = entry._1
          val inEdges = entry._2.getOrElse(true, Set()).toList.sorted.toArray
          val outEdges = entry._2.getOrElse(false, Set()).toList.sorted.toArray
          (relation -> (inEdges, outEdges))
        }).toMap, edgeDict)
      }
    })
    new Graph(finalized, nodeDict, edgeDict)
  }
}

class Graph(entries: Array[Node], val nodeDict: Dictionary, val edgeDict: Dictionary) {
  val size = entries.size
  def getNode(i: Int) = entries(i)
  def getNode(name: String) = entries(nodeDict.getIndex(name))
}

class Node(val edges: Map[Int, (Array[Int], Array[Int])], edgeDict: Dictionary) {
  def getEdges(edgeLabel: String) = {
    edges(edgeDict.getIndex(edgeLabel))
  }
}
