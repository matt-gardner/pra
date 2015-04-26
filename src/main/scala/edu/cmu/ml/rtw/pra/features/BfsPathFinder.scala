package edu.cmu.ml.rtw.pra.features

import java.util.{Map => JavaMap}
import java.util.{Set => JavaSet}

import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
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
  type Graph = Array[Map[Int, (Array[Int], Array[Int])]]

  val allowedKeys = Seq("type", "number of steps", "max fan out")
  JsonHelper.ensureNoExtras(params, "pra parameters -> features -> path finder", allowedKeys)

  // This is number of steps from each side, so a 2 here means you can find paths up to length 4.
  val numSteps = JsonHelper.extractWithDefault(params, "number of steps", 2)

  // If any node has more edges of a single type connected to it than this, we give up the walk at
  // that node.
  val maxFanOut = JsonHelper.extractWithDefault(params, "max fan out", 100)

  lazy val graph: Graph = loadGraph(config.graph, config.nodeDict.getNextIndex)
  var results: Map[(Int, Int), Subgraph] = null
  val factory = new BasicPathTypeFactory
  val pathDict = new Index[PathType](factory)

  override def findPaths(config: PraConfig, data: Dataset, edgesToExclude: Seq[((Int, Int), Int)]) {
    // In part I'm just doing this here to make sure that the graph gets loaded before we start
    // making parallel calls.  I've had issues with bad interactions between par calls and lazy
    // initialization in the past...
    println(s"Graph has ${graph.size} nodes")
    results = runBfs(graph, data, config.unallowedEdges.asScala.map(_.toInt).toSet)
  }

  override def getPathCounts(): JavaMap[PathType, Integer] = {
    throw new NotImplementedError
  }

  override def getPathCountMap(): JavaMap[Pair[Integer, Integer], JavaMap[PathType, Integer]] = {
    throw new NotImplementedError
  }

  override def getLocalSubgraphs() = {
    results.map(entry => {
      (Pair.makePair[Integer, Integer](entry._1._1, entry._1._2) -> entry._2)
    }).asJava
  }

  override def finished() { }

  def loadGraph(graphFile: String, numNodes: Int): Graph = {
    println(s"Loading graph")
    type MutableGraphEntry = mutable.HashMap[Int, mutable.HashMap[Boolean, Set[Int]]]
    val graph = new Array[MutableGraphEntry](numNodes)
    (0 until numNodes).par.foreach(i => { graph(i) = new MutableGraphEntry })
    val lines = fileUtil.readLinesFromFile(graphFile).asScala
    val reader = fileUtil.getBufferedReader(graphFile)
    var line: String = null
    while ({ line = reader.readLine; line != null }) {
      val fields = line.split("\t")
      val source = fields(0).toInt
      val target = fields(1).toInt
      val relation = fields(2).toInt
      val sourceMap = graph(source).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
      sourceMap.update(false, sourceMap.getOrElse(false, Set()) + target)
      val targetMap = graph(target).getOrElseUpdate(relation, new mutable.HashMap[Boolean, Set[Int]])
      targetMap.update(true, targetMap.getOrElse(true, Set()) + source)
    }
    val finalized = new Array[Map[Int, (Array[Int], Array[Int])]](numNodes)
    (0 until numNodes).par.foreach(i => {
      if (graph(i) == null) {
        finalized(i) = Map()
      } else {
        finalized(i) = graph(i).map(entry => {
          val relation = entry._1
          val inEdges = entry._2.getOrElse(true, Set()).toList.sorted.toArray
          val outEdges = entry._2.getOrElse(false, Set()).toList.sorted.toArray
          (relation -> (inEdges, outEdges))
        }).toMap
      }
    })
    finalized
  }

  def runBfs(graph: Graph, data: Dataset, unallowedEdges: Set[Int]) = {
    println("Running BFS")
    val sources = data.getAllSources.asScala.map(_.toInt)
    val targets = data.getAllTargets.asScala.map(_.toInt)
    val sourceTargets = sources.zip(targets)
    // Note that we're doing two BFS searches for each instance - one from the source, and one from
    // the target.  But that's extra work!, you might say, because if there are duplicate sources
    // or targets across instances, we should only have to do the BFS once!  That's true, unless
    // you want to hold out the edge from the graph correctly.  What you really want is to run a
    // BFS for each instance holding out just a _single_ edge from the graph - the training edge
    // that you're trying to learn to predict.  If you share the BFS across multiple training
    // instances, you won't be holding out the edges correctly.
    sourceTargets.par.map(sourceTarget => {
      val source = sourceTarget._1
      val target = sourceTarget._2
      val sourceSubgraph = bfsFromNode(source, target, unallowedEdges)
      val oneSidedSource = reKeyBfsResults(source, sourceSubgraph)
      val targetSubgraph = bfsFromNode(target, source, unallowedEdges)
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
      (sourceTarget -> subgraph)
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

  def bfsFromNode(source: Int, target: Int, unallowedRelations: Set[Int]): Map[Int, Set[Int]] = {
    var currentNodes = Map((source -> Set(pathDict.getIndex(factory.emptyPathType))))
    val results = new mutable.HashMap[Int, mutable.HashSet[Int]]
    for (i <- 1 to numSteps) {
      currentNodes = currentNodes.flatMap(nodeEntry => {
        val node = nodeEntry._1
        val pathTypes = nodeEntry._2
        val nodeResults = graph(node).toSeq.flatMap(relationEdges => {
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
                  val newPathType = addToPathType(pathType, relation, true)
                  results.getOrElseUpdate(nextNode, new mutable.HashSet[Int]).add(newPathType)
                  nextNodes.getOrElseUpdate(nextNode, new mutable.HashSet[Int]).add(newPathType)
                  newPathType
                })
              }
            })
            outEdges.foreach(nextNode => {
              if (!shouldSkip(source, target, node, nextNode, relation, unallowedRelations)) {
                pathTypes.foreach(pathType => {
                  val newPathType = addToPathType(pathType, relation, false)
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

  def addToPathType(pathType: Int, relation: Int, isReverse: Boolean): Int = {
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
