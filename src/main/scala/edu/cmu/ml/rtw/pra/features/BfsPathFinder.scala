package edu.cmu.ml.rtw.pra.features

import java.util.{Map => JavaMap}
import java.util.{Set => JavaSet}

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Index
import edu.cmu.ml.rtw.users.matt.util.JsonHelper
import edu.cmu.ml.rtw.users.matt.util.Pair

import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.parallel.ParMap

class BfsPathFinder(
    params: JValue,
    config: PraConfig,
    fileUtil: FileUtil = new FileUtil)  extends PathFinder {
  implicit val formats = DefaultFormats

  val allowedKeys = Seq("type", "number of steps", "max fan out", "path type factory")
  JsonHelper.ensureNoExtras(params, "operation -> features -> path finder", allowedKeys)

  // This is number of steps from each side, so a 2 here means you can find paths up to length 4.
  // On NELL + SVO, it looks like it takes _way_ too much memory to do more than 2 steps here.
  val numSteps = JsonHelper.extractWithDefault(params, "number of steps", 2)

  // If any node has more edges of a single type connected to it than this, we give up the walk at
  // that node.
  val maxFanOut = JsonHelper.extractWithDefault(params, "max fan out", 100)

  var results: Map[Instance, Subgraph] = null

  val factory = createPathTypeFactory(params \ "path type factory")

  override def getLocalSubgraph(instance: Instance, edgesToExclude: Seq[((Int, Int), Int)]): Subgraph = {
    // We're going to ignore the edgesToExclude here, and just use the unallowedEdges from the
    // config object.  I'm not sure how I ended up with two different ways to get the same input,
    // but that needs to be fixed somewhere...
    getSubgraphForInstance(instance, config.unallowedEdges.toSet)
  }

  override def findPaths(config: PraConfig, data: Dataset, edgesToExclude: Seq[((Int, Int), Int)]) {
    print("Running BFS...  ")
    val start = compat.Platform.currentTime
    results = runBfs(data, config.unallowedEdges.toSet)
    val end = compat.Platform.currentTime
    val seconds = (end - start) / 1000.0
    println(s"Took ${seconds} seconds")
  }

  override def getPathCounts(): JavaMap[PathType, Integer] = {
    throw new NotImplementedError
  }

  override def getPathCountMap(): JavaMap[Instance, JavaMap[PathType, Integer]] = {
    results.map(subgraphInstance => {
      val instance = subgraphInstance._1
      val subgraph = subgraphInstance._2
      val converted = subgraph.flatMap(entry => {
        val key = entry._1
        val s = entry._2
        val t = s.filter(i => i._1 == instance.source && i._2 == instance.target)
        if (t.size > 0) {
          Seq(key -> Integer.valueOf(t.size))
        } else {
          Seq()
        }
      }).asJava
      (instance -> converted)
    }).asJava
  }

  override def getLocalSubgraphs() = results

  override def finished() { }

  def runBfs(data: Dataset, unallowedEdges: Set[Int]) = {
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
    instances.par.map(instance => (instance -> getSubgraphForInstance(instance, unallowedEdges))).seq.toMap
  }

  def getSubgraphForInstance(instance: Instance, unallowedEdges: Set[Int]) = {
    val graph = instance.graph
    val source = instance.source
    val target = instance.target
    val result = new mutable.HashMap[PathType, mutable.HashSet[(Int, Int)]]
    val sourceSubgraph = bfsFromNode(graph, source, target, unallowedEdges, result)
    val targetSubgraph = bfsFromNode(graph, target, source, unallowedEdges, result)
    val sourceKeys = sourceSubgraph.keys.toSet
    val targetKeys = targetSubgraph.keys.toSet
    val keysToUse = if (sourceKeys.size > targetKeys.size) targetKeys else sourceKeys
    for (intermediateNode <- keysToUse) {
      for (sourcePath <- sourceSubgraph(intermediateNode);
           targetPath <- targetSubgraph(intermediateNode)) {
         val combinedPath = factory.concatenatePathTypes(sourcePath, targetPath)
         result.getOrElseUpdate(combinedPath, new mutable.HashSet[(Int, Int)]).add((source, target))
       }
    }
    result.mapValues(_.toSet).toMap
  }

  // The return value here is a map of (end node -> path types).  The resultsByPathType is
  // basically what we're after here; we keep around the (end node -> path types) map so that we
  // can do a join on it to reconstruct larger paths, which then get added to resultsByPathType.
  //
  // I simplified this from a method that used a bunch of maps and flat maps instead of a queue to
  // do this search.  I was hoping this would give a nice speed up, but some timing results seem
  // like it's about the same.  At least this way I think the code is easier to understand.
  def bfsFromNode(
      graph: Graph,
      source: Int,
      target: Int,
      unallowedRelations: Set[Int],
      resultsByPathType: mutable.HashMap[PathType, mutable.HashSet[(Int, Int)]]) = {
    // The object in this queue is (what node I'm at, what path type got me there, steps left).  If
    // you really want to detect cycles (and I'm not sure it's worth it), instead of the PathType
    // you should keep a Seq[(Int, Int, Boolean)] with edge types, nodes, and reverse.  You can
    // check the nodes at each step to see if you're at a cycle, then construct a path type from
    // the edge types and reverse when it's needed.
    val queue = new mutable.Queue[(Int, PathType, Int)]
    val resultsByNode = new mutable.HashMap[Int, mutable.HashSet[PathType]].withDefaultValue(
      new mutable.HashSet[PathType]())
    queue += Tuple3(source, factory.emptyPathType, numSteps)
    while (!queue.isEmpty) {
      val (node, pathType, stepsLeft) = queue.dequeue
      if (pathType != factory.emptyPathType) {
        resultsByNode.getOrElseUpdate(node, new mutable.HashSet[PathType]).add(pathType)
        resultsByPathType.getOrElseUpdate(pathType, new mutable.HashSet[(Int, Int)]).add((source, node))
      }
      if (stepsLeft > 0) {
        for (entry <- graph.getNode(node).edges) {
          val relation = entry._1
          val inEdges = entry._2._1
          val outEdges = entry._2._2
          if (inEdges.length + outEdges.length <= maxFanOut) {
            for (nextNode <- inEdges) {
              if (!shouldSkip(source, target, node, nextNode, relation, unallowedRelations)) {
                val nextPathType = factory.addToPathType(pathType, relation, nextNode, true)
                queue += Tuple3(nextNode, nextPathType, stepsLeft - 1)
              }
            }
            for (nextNode <- outEdges) {
              if (!shouldSkip(source, target, node, nextNode, relation, unallowedRelations)) {
                val nextPathType = factory.addToPathType(pathType, relation, nextNode, false)
                queue += Tuple3(nextNode, nextPathType, stepsLeft - 1)
              }
            }
          }
        }
      }
    }

    // NB: I originally had this method return immutable types, because it's the nicer, scala way
    // of doing things.  However, it seems like I get a ~10-20% speedup by removing the conversion
    // to immutable types here.
    resultsByNode
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

  def createPathTypeFactory(params: JValue) = {
    params match {
      case JNothing => new BasicPathTypeFactory
      case JString("BasicPathTypeFactory") => new BasicPathTypeFactory
      // TODO(matt): allow for configuring this parameter in the spec.
      case JString("LexicalizedPathTypeFactory") => new LexicalizedPathTypeFactory(JNothing)
      case other => throw new IllegalStateException("Unrecognized path type factory specification")
    }
  }
}
