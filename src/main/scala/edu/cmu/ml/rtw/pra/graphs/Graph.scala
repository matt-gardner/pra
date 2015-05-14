package edu.cmu.ml.rtw.pra.graphs

import scala.collection.mutable

class GraphBuilder(numNodes: Int) {
  type MutableGraphEntry = mutable.HashMap[Int, mutable.HashMap[Boolean, Set[Int]]]
  val entries = new Array[MutableGraphEntry](numNodes)
  (0 until numNodes).par.foreach(i => { entries(i) = new MutableGraphEntry })

  def addEdge(source: Int, target: Int, relation: Int) {
    val sourceMap = entries(source).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
    sourceMap.update(false, sourceMap.getOrElse(false, Set()) + target)
    val targetMap = entries(target).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
    targetMap.update(true, targetMap.getOrElse(true, Set()) + source)
  }

  def build(): Graph = {
    val finalized = new Array[Node](numNodes)
    (0 until numNodes).par.foreach(i => {
      if (entries(i) == null) {
        finalized(i) = new Node(Map())
      } else {
        finalized(i) = new Node(entries(i).map(entry => {
          val relation = entry._1
          val inEdges = entry._2.getOrElse(true, Set()).toList.sorted.toArray
          val outEdges = entry._2.getOrElse(false, Set()).toList.sorted.toArray
          (relation -> (inEdges, outEdges))
        }).toMap)
      }
    })
    new Graph(finalized)
  }
}

class Graph(entries: Array[Node]) {
  val size = entries.size
  def getNode(i: Int) = entries(i)
}

class Node(val edges: Map[Int, (Array[Int], Array[Int])]) {
}
