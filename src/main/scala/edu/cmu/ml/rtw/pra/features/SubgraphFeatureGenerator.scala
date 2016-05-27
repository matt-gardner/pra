package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.features.extractors.FeatureExtractor
import edu.cmu.ml.rtw.pra.features.extractors.FeatureMatcher
import edu.cmu.ml.rtw.pra.features.extractors.NodeFeatureExtractor
import edu.cmu.ml.rtw.pra.features.extractors.NodePairFeatureExtractor
import edu.cmu.ml.rtw.pra.graphs.Graph

import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.Pair
import com.mattg.util.Vector

import java.io.File

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.json4s._
import org.json4s.Formats
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL.WithDouble._

import gnu.trove.{TIntArrayList => TList}

abstract class SubgraphFeatureGenerator[T <: Instance](
  params: JValue,
  outputter: Outputter,
  val featureDict: MutableConcurrentDictionary = new MutableConcurrentDictionary,
  fileUtil: FileUtil = new FileUtil()
) extends FeatureGenerator[T] {
  implicit val formats = DefaultFormats
  val featureParamKeys = Seq("type", "path finder", "feature extractors", "feature size",
    "include bias", "log level")
  JsonHelper.ensureNoExtras(params, "operation -> features", featureParamKeys)

  val featureSize = JsonHelper.extractWithDefault(params, "feature size", -1)
  val includeBias = JsonHelper.extractWithDefault(params, "include bias", false)
  val logLevel = JsonHelper.extractWithDefault(params, "log level", 3)

  lazy val pathFinder = createPathFinder()
  val emptySubgraph: Subgraph = Map()

  def createPathFinder(): PathFinder[T]

  override def constructMatrixRow(instance: T) =
    extractFeatures(instance, getLocalSubgraph(instance))

  override def createTrainingMatrix(data: Dataset[T]): FeatureMatrix =
    createMatrixFromData(data)

  override def removeZeroWeightFeatures(weights: Seq[Double]): Seq[Double] = weights

  override def createTestMatrix(data: Dataset[T]): FeatureMatrix =
    createMatrixFromData(data)

  def createMatrixFromData(data: Dataset[T]) = {
    outputter.outputAtLevel(s"Creating feature matrix from ${data.instances.size} instances", logLevel)
    val rows = data.instances.par.map(constructMatrixRow).flatten.seq
    new FeatureMatrix(rows.asJava)
  }

  override def getFeatureNames(): Array[String] = {
    // Not really sure if par is useful here...  Maybe I should just take it out.
    // TODO(matt): Shouldn't this just go in the Dictionary code?
    val features = (1 until featureDict.size).par.map(i => {
        val name = featureDict.getString(i)
        if (name == null) {
          // We need to put these in the feature name array, so that MALLET gets the feature
          // indices right.  But these features should never show up in any actual output file,
          // because the index should never appear in a real feature matrix.  We also need to be
          // careful to make these _unique_, or MALLET will think they are the same feature and
          // decrease the size of the dictionary, further messing things up.
          s"!!!!NULL FEATURE-${i}!!!!"
        } else {
          name
        }
    }).seq
    if (includeBias) {
      ("bias" +: features).toArray
    } else {
      ("!!!!NULL FEATURE!!!!" +: features).toArray
    }
  }

  val featureExtractors = createExtractors(params)

  def createExtractors(params: JValue): Seq[FeatureExtractor[T]]

  def getLocalSubgraphs(data: Dataset[T]): Map[T, Subgraph] = {
    outputter.outputAtLevel(s"Finding local subgraphs with ${data.instances.size} training instances",
      logLevel)

    pathFinder.findPaths(data)

    pathFinder.getLocalSubgraphs
  }

  def getLocalSubgraph(instance: T): Subgraph = {
    if (instance.isInGraph()) {
      pathFinder.getLocalSubgraph(instance)
    } else {
      emptySubgraph
    }
  }

  def extractFeatures(instance: T, subgraph: Subgraph): Option[MatrixRow] = {
    val features = featureExtractors.flatMap(_.extractFeatures(instance, subgraph))
    if (features.size > 0) {
      Some(createMatrixRow(instance, features.toSet.map(featureToIndex).toSeq.sorted))
    } else {
      None
    }
  }

  def extractFeaturesAsStrings(instance: T, subgraph: Subgraph): Seq[String] = {
    featureExtractors.flatMap(_.extractFeatures(instance, subgraph))
  }

  def extractFeatures(subgraphs: Map[T, Subgraph]): FeatureMatrix = {
    val matrix_rows = subgraphs.par.flatMap(entry => {
      extractFeatures(entry._1, entry._2) match {
        case Some(row) => Seq(row)
        case None => Seq()
      }
    }).seq.toList
    new FeatureMatrix(matrix_rows.asJava)
  }

  def featureToIndex(feature: String): Int = {
    if (featureSize == -1) {
      featureDict.getIndex(feature)
    } else {
      val hash = feature.hashCode % featureSize
      if (hash >= 0)
        featureDict.getIndex(s"hash-${hash}")
      else
        featureDict.getIndex(s"hash-${hash + featureSize}")
    }
  }

  def createMatrixRow(instance: T, features: Seq[Int]): MatrixRow = {
    val size = if (includeBias) features.size + 1 else features.size
    val values = new Array[Double](size)
    for (i <- 0 until size) {
      values(i) = 1
    }
    val featureIndices = if (includeBias) features :+ 0 else features
    new MatrixRow(instance, featureIndices.toArray, values)
  }
}

class NodePairSubgraphFeatureGenerator(
  params: JValue,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  featureDict: MutableConcurrentDictionary = new MutableConcurrentDictionary,
  fileUtil: FileUtil = new FileUtil()
) extends SubgraphFeatureGenerator[NodePairInstance](params, outputter, featureDict, fileUtil) {

  def createExtractors(params: JValue): Seq[FeatureExtractor[NodePairInstance]] = {
    val extractorNames: List[JValue] = JsonHelper.extractWithDefault(params, "feature extractors",
      List(JString("PraFeatureExtractor").asInstanceOf[JValue]))
    extractorNames.map(params => NodePairFeatureExtractor.create(params, outputter, fileUtil))
  }

  def createPathFinder() = NodePairPathFinder.create(params \ "path finder", relation,
    relationMetadata, outputter, fileUtil)

  // This method finds nodes in the graph that are connected by the set of input features.  This is
  // essentially going backward from feature generation; instead of taking a subgraph between two
  // nodes and finding features, we're taking features and finding a subgraph (which then gives us
  // a set of nodes to return).  This is really similar to the PathFollower logic in the old PRA
  // way of doing things, but does not worry about any kind of probability calculation (and the
  // features are potentially more complicated, so some parts of this are a little trickier).
  //
  // The parameters are the node to search from, whether this node is the source entity or the
  // target entity, the set of features to test, and the graph to use for the testing.
  //
  // TODO(matt): This is actually getting a bit involved...  Maybe I should move this code to a new
  // object...
  def getRelatedNodes(
    node: String,
    isSource: Boolean,
    features: Seq[String],
    graph: Graph
  ): Set[String] = {
    features.par.flatMap(feature => {
      val matchers = featureExtractors.flatMap(extractor => {
        val npExtractor = extractor.asInstanceOf[NodePairFeatureExtractor]
        npExtractor.getFeatureMatcher(feature, isSource, graph) match {
          case None => Seq()
          case Some(matcher) => Seq(matcher)
        }
      }).toSet  // toSet here because there's potentially some overlap in the feature extractors
      matchers.flatMap(matcher => findMatchingNodes(node, matcher, graph)).toSet
    }).seq.toSet -- Set(node)
  }

  def findMatchingNodes(
    nodeName: String,
    featureMatcher: FeatureMatcher[NodePairInstance],
    graph: Graph
  ): Set[String] = {
    if (!graph.hasNode(nodeName)) return Set()
    val queue = new mutable.Queue[(Int, Int)]  // (nodeId, stepsTaken)
    val foundNodes = new mutable.HashSet[String]

    val nodeId = graph.getNodeIndex(nodeName)
    queue += Tuple2(nodeId, 0)
    while (!queue.isEmpty) {
      val (currentNodeId, stepsTaken) = queue.dequeue
      // Are we done?
      if (!featureMatcher.isFinished(stepsTaken)) {
        // No, we're not done.  Follow another edge type.
        val node = graph.getNode(currentNodeId)
        featureMatcher.allowedEdges(stepsTaken) match {
          case None => {
            // We weren't given an allowed edge type, so we just need to try all of them that are
            // at this node.
            for (edgeType <- node.edges.keys) {
              if (featureMatcher.edgeOk(edgeType, true, stepsTaken)) {
                val connected = node.edges.get(edgeType)._1
                queueNextNodes(connected, stepsTaken, featureMatcher, queue)
              }
              if (featureMatcher.edgeOk(edgeType, false, stepsTaken)) {
                val connected = node.edges.get(edgeType)._2
                queueNextNodes(connected, stepsTaken, featureMatcher, queue)
              }
            }
          }
          case Some(edgeTypes) => {
            // For each of the allowed edge types, we see if the current node has the edge type,
            // then queue up the allowed nodes connected to that edge type.
            for ((edgeType, reverse) <- edgeTypes) {
              if (node.edges.containsKey(edgeType)) {
                val n = if (reverse) node.edges.get(edgeType)._1 else node.edges.get(edgeType)._2
                queueNextNodes(n, stepsTaken, featureMatcher, queue)
              }
            }
          }
        }
      } else {
        // Yes, we're done.  Is this an allowed final node?
        if (featureMatcher.nodeOk(currentNodeId, stepsTaken)) {
          foundNodes += graph.getNodeName(currentNodeId)
        }
      }
    }
    foundNodes.toSet
  }

  // Given the list of nodes connected to the current node, queue the ones that are allowed.
  private def queueNextNodes(
    connectedNodes: TList,
    stepsTaken: Int,
    featureMatcher: FeatureMatcher[NodePairInstance],
    queue: mutable.Queue[(Int, Int)]
  ) {
    featureMatcher.allowedNodes(stepsTaken) match {
      case None => {
        // If the matcher doesn't specify a priori which nodes are allowed, we check all of them
        // and add them if applicable.
        for (i <- 0 until connectedNodes.size) {
          val nextNode = connectedNodes.get(i)
          if (featureMatcher.nodeOk(nextNode, stepsTaken)) {
            queue += Tuple2(nextNode, stepsTaken + 1)
          }
        }
      }
      case Some(allowedNodes) => {
        // Here we just check to see which of the allowed nodes are present.  The efficiency of
        // contains() on the TList might not be great, but I don't think it'll be a big issue here.
        for (node <- allowedNodes) {
          if (connectedNodes.contains(node)) {
            queue += Tuple2(node, stepsTaken + 1)
          }
        }
      }
    }
  }
}

class NodeSubgraphFeatureGenerator(
  params: JValue,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  featureDict: MutableConcurrentDictionary = new MutableConcurrentDictionary,
  fileUtil: FileUtil = new FileUtil()
) extends SubgraphFeatureGenerator[NodeInstance](params, outputter, featureDict, fileUtil) {

  def createExtractors(params: JValue): Seq[FeatureExtractor[NodeInstance]] = {
    val extractorNames: List[JValue] = JsonHelper.extractWithDefault(params, "feature extractors",
      List(JString("PathOnlyFeatureExtractor").asInstanceOf[JValue]))
    extractorNames.map(params => NodeFeatureExtractor.create(params, outputter, fileUtil))
  }

  def createPathFinder() =
    NodePathFinder.create(params \ "path finder", relation, relationMetadata, outputter)
}
