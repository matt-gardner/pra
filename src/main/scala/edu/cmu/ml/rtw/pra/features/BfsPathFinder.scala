package edu.cmu.ml.rtw.pra.features

import java.util.{Map => JavaMap}
import java.util.{Set => JavaSet}

import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Index
import edu.cmu.ml.rtw.users.matt.util.Pair

import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.parallel.ParMap

class BfsPathFinder(
    params: JValue,
    config: PraConfig,
    praBase: String,
    fileUtil: FileUtil = new FileUtil)  extends PathFinder {
  implicit val formats = DefaultFormats
  type Subgraph = JavaMap[PathType, JavaSet[Pair[Integer, Integer]]]

  val allowedKeys = Seq("type", "number of steps", "max fan out")
  JsonHelper.ensureNoExtras(params, "pra parameters -> features -> path finder", allowedKeys)

  // This is number of steps from each side, so a 2 here means you can find paths up to length 4.
  // On NELL + SVO, it looks like it takes _way_ too much memory to do more than 2 steps here.
  val numSteps = JsonHelper.extractWithDefault(params, "number of steps", 2)

  // If any node has more edges of a single type connected to it than this, we give up the walk at
  // that node.
  val maxFanOut = JsonHelper.extractWithDefault(params, "max fan out", 100)

  var results: Map[Instance, Subgraph] = null
  // TODO(matt): allow for configuring this.  On small instance-specific graphs, you might want to
  // lexicalize the nodes in the graph.
  val factory = new BasicPathTypeFactory

  override def findPaths(config: PraConfig, data: Dataset, edgesToExclude: Seq[((Int, Int), Int)]) {
    results = runBfs(data, config.unallowedEdges.map(_.toInt).toSet)
  }

  override def getPathCounts(): JavaMap[PathType, Integer] = {
    throw new NotImplementedError
  }

  override def getPathCountMap(): JavaMap[Instance, JavaMap[PathType, Integer]] = {
    throw new NotImplementedError
  }

  override def getLocalSubgraphs() = results.asJava

  override def finished() { }

  def runBfs(data: Dataset, unallowedEdges: Set[Int]) = {
    println("Running BFS")
    val instances = data.instances
    // This line is just to make sure the graph gets loaded (lazily) before the parallel calls, if
    // the data is using a shared graph.
    instances(0).graph match {
      case onDisk: GraphOnDisk => { onDisk.entries.size }
      case inMemory: GraphInMemory => {}
    }
    // Note that we're doing two BFS searches for each instance - one from the source, and one from
    // the target.  But that's extra work!, you might say, because if there are duplicate sources
    // or targets across instances, we should only have to do the BFS once!  That's true, unless
    // you want to hold out the edge from the graph correctly.  What you really want is to run a
    // BFS for each instance holding out just a _single_ edge from the graph - the training edge
    // that you're trying to learn to predict.  If you share the BFS across multiple training
    // instances, you won't be holding out the edges correctly.
    instances.par.map(instance => {
      val pathDict = new Index[PathType](factory)
      val graph = instance.graph
      val source = instance.source
      val target = instance.target
      val sourceSubgraph = bfsFromNode(graph, source, target, unallowedEdges, pathDict)
      val oneSidedSource = reKeyBfsResults(source, sourceSubgraph)
      val targetSubgraph = bfsFromNode(graph, target, source, unallowedEdges, pathDict)
      val oneSidedTarget = reKeyBfsResults(target, targetSubgraph)
      val intersection = sourceSubgraph.keys.toSet.intersect(targetSubgraph.keys.toSet)
      val twoSided = intersection.flatMap(node => {
        val sourcePaths = sourceSubgraph(node)
        val targetPaths = targetSubgraph(node)
        val combinedPaths = for (sp <- sourcePaths; tp <- targetPaths)
           yield (factory.concatenatePathTypes(pathDict.getKey(sp), pathDict.getKey(tp)), (source, target))
        combinedPaths.groupBy(_._1).mapValues(_.map(_._2))
      })
      val result = new mutable.HashMap[PathType, mutable.HashSet[(Int, Int)]]
      oneSidedSource.foreach(entry => {
        result.getOrElseUpdate(pathDict.getKey(entry._1), new mutable.HashSet[(Int, Int)]).++=(entry._2)
      })
      oneSidedTarget.foreach(entry => {
        result.getOrElseUpdate(pathDict.getKey(entry._1), new mutable.HashSet[(Int, Int)]).++=(entry._2)
      })
      twoSided.foreach(entry => {
        result.getOrElseUpdate(entry._1, new mutable.HashSet[(Int, Int)]).++=(entry._2)
      })
      val subgraph = result.mapValues(_.map(convertToPair).seq.toSet.asJava).seq.asJava
      (instance -> subgraph)
    }).seq.toMap
  }

  def reKeyBfsResults(origin: Int, bfsResults: Map[Int, Set[Int]]) = {
    val rekeyed = new mutable.HashMap[Int, mutable.HashSet[(Int, Int)]]
    bfsResults.foreach(endNodePaths => {
      val endNode = endNodePaths._1
      val paths = endNodePaths._2
      paths.foreach(pathType => {
        rekeyed.getOrElseUpdate(pathType, new mutable.HashSet[(Int, Int)]).add((origin, endNode))
      })
    })
    rekeyed.mapValues(_.toSet).toMap
  }

  def bfsFromNode(
      graph: Graph,
      source: Int,
      target: Int,
      unallowedRelations: Set[Int],
      pathDict: Index[PathType]) = {
    var currentNodes = Map((source -> Set(pathDict.getIndex(factory.emptyPathType))))
    val results = new mutable.HashMap[Int, mutable.HashSet[Int]]
    for (i <- 1 to numSteps) {
      currentNodes = currentNodes.flatMap(nodeEntry => {
        val node = nodeEntry._1
        val pathTypes = nodeEntry._2
        val nodeResults = graph.getNode(node).edges.toSeq.flatMap(relationEdges => {
          val relation = relationEdges._1
          val inEdges = relationEdges._2._1
          val outEdges = relationEdges._2._2
          if (inEdges.length + outEdges.length > maxFanOut) {
            Seq()
          } else {
            val nextNodes = new mutable.HashMap[Int, mutable.HashSet[Int]]
            inEdges.foreach(nextNode => {
              if (!shouldSkip(source, target, node, nextNode, relation, unallowedRelations)) {
                pathTypes.foreach(pathType => {
                  val newPathType = addToPathType(pathType, relation, true, pathDict)
                  results.getOrElseUpdate(nextNode, new mutable.HashSet[Int]).add(newPathType)
                  nextNodes.getOrElseUpdate(nextNode, new mutable.HashSet[Int]).add(newPathType)
                  newPathType
                })
              }
            })
            outEdges.foreach(nextNode => {
              if (!shouldSkip(source, target, node, nextNode, relation, unallowedRelations)) {
                pathTypes.foreach(pathType => {
                  val newPathType = addToPathType(pathType, relation, false, pathDict)
                  results.getOrElseUpdate(nextNode, new mutable.HashSet[Int]).add(newPathType)
                  nextNodes.getOrElseUpdate(nextNode, new mutable.HashSet[Int]).add(newPathType)
                  newPathType
                })
              }
            })
            nextNodes.toSeq.map(entry => (entry._1, entry._2.toSet))
          }
        })
        nodeResults.groupBy(_._1).mapValues(_.flatMap(_._2).toSet)
      })
    }
    results.map(entry => (entry._1 -> entry._2.toSet)).toMap
  }

  def shouldSkip(source: Int, target: Int, node1: Int, node2: Int, relation: Int, exclude: Set[Int]) = {
    if (!(source == node1 && target == node2) && !(source == node2 && target == node1))
      false
    else if (exclude.contains(relation))
      true
    else
      false
  }

  def addToPathType(pathType: Int, relation: Int, isReverse: Boolean, pathDict: Index[PathType]): Int = {
    try {
      val prevString = pathDict.getKey(pathType).encodeAsString()
      val newString = if (isReverse) prevString + "_" + relation + "-" else prevString + relation + "-"
      pathDict.getIndex(factory.fromString(newString))
    } catch {
      case e: NullPointerException => {
        println(s"NULL PATH TYPE: $pathType")
        throw e
      }
    }
  }

  def convertToPair(entry: (Int, Int)): Pair[Integer, Integer] = {
    Pair.makePair(entry._1, entry._2)
  }
}
