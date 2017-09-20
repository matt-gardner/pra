package edu.cmu.ml.rtw.pra.features

import java.util.{Map => JavaMap}
import java.util.{Set => JavaSet}

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import com.mattg.util.FileUtil
import com.mattg.util.Index
import com.mattg.util.JsonHelper
import com.mattg.util.Pair

import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.parallel.ParMap

abstract class BfsPathFinder[T <: Instance](
  params: JValue,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
)  extends PathFinder[T] {
  implicit val formats = DefaultFormats

  val allowedKeys = Seq("type", "number of steps", "max fan out", "path type factory", "log level")
  JsonHelper.ensureNoExtras(params, "operation -> features -> path finder", allowedKeys)

  // This is number of steps from each side, so a 2 here means you can find paths up to length 4.
  // On NELL + SVO, it looks like it takes _way_ too much memory to do more than 2 steps here.
  val numSteps = JsonHelper.extractWithDefault(params, "number of steps", 2)

  // If any node has more edges of a single type connected to it than this, we give up the walk at
  // that node.
  val maxFanOut = JsonHelper.extractWithDefault(params, "max fan out", 100)

  val factoryParams = params \ "path type factory"
  val logLevel = JsonHelper.extractWithDefault(params, "log level", 3)

  var results: Map[T, Subgraph] = null

  override def getLocalSubgraph(instance: T): Subgraph = {
    getSubgraphForInstance(instance)
  }

  override def findPaths(data: Dataset[T]) {
    outputter.outputAtLevel("Running BFS...  ", logLevel)
    val start = compat.Platform.currentTime
    results = runBfs(data)
    val end = compat.Platform.currentTime
    val seconds = (end - start) / 1000.0
    outputter.outputAtLevel(s"Took ${seconds} seconds", logLevel)
  }

  override def getPathCounts(): JavaMap[PathType, Integer] = {
    throw new NotImplementedError
  }

  override def getPathCountMap(): JavaMap[T, JavaMap[PathType, Integer]] = {
    results.map(subgraphInstance => {
      val instance = subgraphInstance._1
      val subgraph = subgraphInstance._2
      val converted = subgraph.flatMap(entry => {
        val pathType = entry._1
        val nodePairSet = entry._2
        val keptNodePairs = nodePairSet.filter(pair => nodePairMatchesInstance(pair, instance))
        if (keptNodePairs.size > 0) {
          Seq(pathType -> Integer.valueOf(keptNodePairs.size))
        } else {
          Seq()
        }
      }).asJava
      (instance -> converted)
    }).asJava
  }

  def nodePairMatchesInstance(pair: (Int, Int), instance: T): Boolean

  override def getLocalSubgraphs() = results
  def getSubgraphForInstance(instance: T): Subgraph

  override def finished() { }

  def runBfs(data: Dataset[T]) = {
    val instances = data.instances
    // This line is just to make sure the graph gets loaded (lazily) before the parallel calls, if
    // the data is using a shared graph.
    instances(0).graph match {
      case onDisk: GraphOnDisk => { onDisk.entries.size }
      case inMemory: GraphInMemory => {}
    }
    instances.par.map(instance => (instance -> getSubgraphForInstance(instance))).seq.toMap
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
    factory: PathTypeFactory,
    source: Int,
    target: Int,
    unallowedRelations: Set[Int],
    resultsByPathType: mutable.HashMap[PathType, mutable.HashSet[(Int, Int)]]
  ) = {
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
        val n = graph.getNode(node)
        for (relation <- n.edges.keys) {
          val edges = n.edges.get(relation)
          val inEdges = edges._1
          val outEdges = edges._2
          if (inEdges.size + outEdges.size <= maxFanOut) {
            for (i <- 0 until inEdges.size) {
              val nextNode = inEdges.get(i)
              if (!shouldSkip(source, target, node, nextNode, relation, unallowedRelations)) {
                val nextPathType = factory.addToPathType(pathType, relation, nextNode, true)
                queue += Tuple3(nextNode, nextPathType, stepsLeft - 1)
              }
            }
            for (i <- 0 until outEdges.size) {
              val nextNode = outEdges.get(i)
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

  def createPathTypeFactory(params: JValue, graph: Graph) = {
    params match {
      case JNothing => new BasicPathTypeFactory(graph)
      case JString("BasicPathTypeFactory") => new BasicPathTypeFactory(graph)
      // TODO(matt): allow for configuring this parameter in the spec.
      case JString("LexicalizedPathTypeFactory") => new LexicalizedPathTypeFactory(JNothing, graph)
      case other => throw new IllegalStateException("Unrecognized path type factory specification")
    }
  }
}

class NodePairBfsPathFinder(
  params: JValue,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
) extends BfsPathFinder[NodePairInstance](params, relation, relationMetadata, outputter, fileUtil) {
  def getSubgraphForInstance(instance: NodePairInstance) = {
    val graph = instance.graph
    val factory = createPathTypeFactory(factoryParams, graph)
    val unallowedEdges = relationMetadata.getUnallowedEdges(relation, graph).toSet
    val source = instance.source
    val target = instance.target
    val result = new mutable.HashMap[PathType, mutable.HashSet[(Int, Int)]]

    // Note that we're doing two BFS searches for each instance - one from the source, and one from
    // the target.  But that's extra work!, you might say, because if there are duplicate sources
    // or targets across instances, we should only have to do the BFS once!  That's true, unless
    // you want to hold out the edge from the graph correctly.  What you really want is to run a
    // BFS for each instance holding out just a _single_ edge from the graph - the training edge
    // that you're trying to learn to predict.  If you share the BFS across multiple training
    // instances, you won't be holding out the edges correctly.
    //
    // It might make sense to have a setting where you _know_ that you don't need to hold out any
    // edges from the graph, so you can re-use the BFS for each source node.  Except, we've gone
    // away from bulk processing towards processing individual instances, anyway, for memory
    // reasons.  This setting would require keeping these subgraphs around in memory, and they are
    // very large...
    val sourceSubgraph = bfsFromNode(graph, factory, source, target, unallowedEdges, result)
    val targetSubgraph = bfsFromNode(graph, factory, target, source, unallowedEdges, result)
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

  override def nodePairMatchesInstance(pair: (Int, Int), instance: NodePairInstance): Boolean = {
    pair._1 == instance.source && pair._2 == instance.target
  }
}

class NodeBfsPathFinder(
  params: JValue,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
) extends BfsPathFinder[NodeInstance](params, relation, relationMetadata, outputter, fileUtil) {
  def getSubgraphForInstance(instance: NodeInstance) = {
    val graph = instance.graph
    val factory = createPathTypeFactory(factoryParams, graph)
    val unallowedEdges = relationMetadata.getUnallowedEdges(relation, graph).toSet
    val node = instance.node
    // There's no target to watch out for if we only have a NodeInstance.
    val fakeTarget = -1
    val result = new mutable.HashMap[PathType, mutable.HashSet[(Int, Int)]]
    val subgraph = bfsFromNode(graph, factory, node, fakeTarget, unallowedEdges, result)
    result.mapValues(_.toSet).toMap
  }

  override def nodePairMatchesInstance(pair: (Int, Int), instance: NodeInstance): Boolean = {
    pair._1 == instance.node
  }
}
