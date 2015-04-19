package edu.cmu.ml.rtw.pra.features

import java.util.{Map => JavaMap}
import java.util.{Set => JavaSet}

import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.parallel.ParMap

class BfsPathFinder(params: JValue, praBase: String, fileUtil: FileUtil = new FileUtil)  extends PathFinder {
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

  var results: Map[(Int, Int), Subgraph] = null

  override def findPaths(config: PraConfig, data: Dataset, edgesToExclude: Seq[((Int, Int), Int)]) {
    val graph = loadGraph(config.graph, config.nodeDict.getNextIndex)
    results = mergeBfsResults(data, runBfs(graph, data))
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
    val graph = new Array[Map[Int, (Array[Int], Array[Int])]](numNodes)
    val lines = fileUtil.readLinesFromFile(graphFile).asScala
    val edges = lines.par.flatMap(line => {
      val fields = line.split("\t")
      val source = fields(0).toInt
      val target = fields(1).toInt
      val relation = fields(2).toInt
      Seq((source, target, relation, true), (target, source, relation, false))
    })
    edges.groupBy(_._1).foreach(edgeSet => {
      val node = edgeSet._1
      val relationMap = edgeSet._2.groupBy(_._3).map(relationEdges => {
        val relation = relationEdges._1
        val inOut = relationEdges._2.groupBy(_._4).mapValues(_.map(_._2).toList.sorted.toArray)
        val inEdges = inOut.getOrElse(false, new Array[Int](0))
        val outEdges = inOut.getOrElse(true, new Array[Int](0))
        (relation -> (inEdges, outEdges))
      })
      graph(node) = relationMap.seq
    })
    (0 until numNodes).par.foreach(i => if (graph(i) == null) graph(i) = Map())
    graph
  }

  def runBfs(graph: Graph, data: Dataset): Map[Int, Map[Int, Set[PathType]]] = {
    val sources = data.getAllSources.asScala.map(_.toInt).toSet ++ data.getAllTargets.asScala.map(_.toInt).toSet
    var walkResult: ParMap[Int, Map[Int, Set[PathType]]] = sources.par.map(s => (s -> null)).toMap
    for (i <- 1 to numSteps) {

      val stepResults = walkResult.flatMap(entry => {
        val currentNode = entry._1
        val pathsSoFar = entry._2
        graph(currentNode).flatMap(relationEdges => {
          val relation = relationEdges._1
          val inEdges = relationEdges._2._1
          val outEdges = relationEdges._2._2
          val edges = inEdges.toSeq.map(x => (x, true)) ++ outEdges.toSeq.map(x => (x, false))
          if (edges.size < maxFanOut) {
            edges.flatMap(edge => {
              val nextNode = edge._1
              val isInEdge = edge._2
              if (pathsSoFar == null) {
                Seq((nextNode, currentNode, addToPathType(null, relation, isInEdge)))
              } else {
                for (sourceMap <- pathsSoFar;
                     pathType <- sourceMap._2)
                   yield (nextNode, sourceMap._1, addToPathType(pathType, relation, isInEdge))
              }
            })
          } else {
            Seq()
          }
        })
      })
      val grouped = stepResults.groupBy(_._1).mapValues(entries => {
        entries.groupBy(_._2).mapValues(_.map(_._3).seq.toSet).seq.toMap
      })
      walkResult = mergeResults(walkResult, grouped)
    }

    walkResult.seq.toMap
  }

  def mergeResults(
      result1: ParMap[Int, Map[Int, Set[PathType]]],
      result2: ParMap[Int, Map[Int, Set[PathType]]]): ParMap[Int, Map[Int, Set[PathType]]] = {
    val keys = result1.keys ++ result2.keys
    keys.map(key => {
      val map1 = result1.getOrElse(key, Map())
      val map2 = result2.getOrElse(key, Map())
      val mapKeys = if (map1 == null) map2.keys else map1.keys ++ map2.keys
      val innerMap = mapKeys.map(mapKey => {
        val paths1 = map1.getOrElse(mapKey, Set())
        val paths2 = map2.getOrElse(mapKey, Set())
        (mapKey -> (paths1 ++ paths2))
      }).toMap
      (key -> innerMap)
    }).toMap
  }

  val factory = new BasicPathTypeFactory
  def addToPathType(pathType: PathType, relation: Int, isReverse: Boolean): PathType = {
    val prevString = if (pathType == null) "-" else pathType.encodeAsString()
    val newString = if (isReverse) s"${prevString}_${relation}-" else s"${prevString}${relation}-"
    factory.fromString(newString)
  }

  def mergeBfsResults(data: Dataset, bfsResults: Map[Int, Map[Int, Set[PathType]]]) = {
    val sources = data.getAllSources.asScala.map(_.toInt).toSet
    val targets = data.getAllTargets.asScala.map(_.toInt).toSet
    val sourceTargets = sources.zip(targets)
    val emptyPath = factory.emptyPathType()
    val pairPaths = bfsResults.par.flatMap(entry => {
      val intermediate = entry._1
      val originPaths = entry._2
      // First we just output the (one-sided) source-intermediate pair, with each path type found.
      val oneSided = originPaths.flatMap(sourcePath => {
        val source = sourcePath._1
        sourcePath._2.map(path => (true, (source, intermediate), path))
      })
      // Now we explicitly find longer paths that hinge on intermediate nodes.
      val twoSided =
        // First we check for the case where the intermediate node is actually a source, meaning
        // that we got here from a target node.
        if (sources.contains(intermediate)) {
          val source = intermediate
          originPaths.flatMap(targetPath => {
            val target = targetPath._1
            if (sourceTargets.contains((source, target))) {
              // If the _intermediate_ node is the source, then we need to reverse the path types.
              targetPath._2.map(path => (false, (source, target),
                factory.concatenatePathTypes(emptyPath, path)))
            } else {
              Seq()
            }
          })
        // Then we check the case where the intermediate node is a target node, meaning we got here
        // directly from a source.
        } else if (targets.contains(intermediate)) {
          val target = intermediate
          originPaths.flatMap(sourcePath => {
            val source = sourcePath._1
            if (sourceTargets.contains((source, target))) {
              sourcePath._2.map(path => (false, (source, target), path))
            } else {
              Seq()
            }
          })
        // Finally, we check the case where we got to this node from both a source and a target.
        } else {
          originPaths.flatMap(sourcePath => {
          val source = sourcePath._1
            originPaths.flatMap(targetPath => {
              val target = targetPath._1
              if (sourceTargets.contains((source, target))) {
                for (sp <- sourcePath._2;
                     tp <- targetPath._2)
                   yield (false, (source, target), factory.concatenatePathTypes(sp, tp))
              } else {
                Seq()
              }
            })
          })
        }
      oneSided ++ twoSided
    })
    val grouped = pairPaths.groupBy(_._1)
    val twoSided = grouped(false).groupBy(_._2)
    val oneSided = grouped(true).groupBy(_._2._1)
    sourceTargets.par.map(sourceTarget => {
      val source = sourceTarget._1
      val target = sourceTarget._2
      val paths = oneSided(source) ++ oneSided(target) ++ twoSided(sourceTarget)
      val subgraph = paths.groupBy(_._3).mapValues(_.map(convertToPair).seq.toSet.asJava).seq.asJava
      (sourceTarget -> subgraph)
    }).seq.toMap
  }

  def convertToPair(entry: (Boolean, (Int, Int), PathType)): Pair[Integer, Integer] = {
    Pair.makePair(entry._2._1, entry._2._2)
  }
}
