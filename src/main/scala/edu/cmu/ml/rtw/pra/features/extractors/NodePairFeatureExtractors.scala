package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.BaseEdgeSequencePathType
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.LexicalizedPathType
import edu.cmu.ml.rtw.pra.features.Subgraph
import edu.cmu.ml.rtw.pra.graphs.Graph

import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper

import scala.util.control.Exception.allCatch
import scala.math.log10
import scala.math.abs

import org.json4s._

trait NodePairFeatureExtractor extends FeatureExtractor[NodePairInstance] {

  /**
   * This method essentially goes backward from a FeatureExtractor.
   * FeatureExtractor.extractFeatures lets you take a subgraph and get features out.  This method
   * takes features in, then lets you filter the graph to only subgraphs that would have generated
   * this feature.
   *
   * The default implementation here is to return None, and we will override it in the few cases
   * where I've put in the effort to make feature matching work.
   *
   * I originally had this as part of FeatureExtractor, with this overriding a base method there.
   * But, I need to have startFromSourceNode as part of this method, and it's not applicable to
   * NodeInstances.  So, I'm just putting it here on this class.
   *
   * @param feature A string description of the feature we're trying to match.  If the extractor
   * did not produce this string, or the feature is otherwise unmatchable, we will return None.
   * @param startFromSourceNode We could be given either the source node or the target node and
   * asked to match features; this tells us which one we're trying to match.
   * @param graph The graph is necessary to go from the human-readable string representation of the
   * feature to actual edge ids.
   */
  def getFeatureMatcher(
    feature: String,
    startFromSourceNode: Boolean,
    graph: Graph
  ): Option[FeatureMatcher[NodePairInstance]] = None
}

object NodePairFeatureExtractor {
  def create(
    params: JValue,
    outputter: Outputter,
    fileUtil: FileUtil
  ): NodePairFeatureExtractor = {
    params match {
      case JString("PraFeatureExtractor") => new PraFeatureExtractor(JNothing)
      case JString("PathBigramsFeatureExtractor") => new PathBigramsFeatureExtractor
      case JString("OneSidedPathAndEndNodeFeatureExtractor") => new OneSidedPathAndEndNodeFeatureExtractor(outputter)
      case JString("OneSidedPathOnlyFeatureExtractor") => new OneSidedPathOnlyFeatureExtractor(outputter)
      case JString("CategoricalComparisonFeatureExtractor") => new CategoricalComparisonFeatureExtractor
      case JString("NumericalComparisonFeatureExtractor") => new NumericalComparisonFeatureExtractor
      case JString("AnyRelFeatureExtractor") => new AnyRelFeatureExtractor
      case JString("AnyRelAliasOnlyFeatureExtractor") => new AnyRelAliasOnlyFeatureExtractor
      case JString("ConnectedAtOneFeatureExtractor") => new ConnectedAtOneFeatureExtractor(JNothing)
      case JString("ConnectedByMediatorFeatureExtractor") => new ConnectedByMediatorFeatureExtractor(JNothing)
      case JString(other) => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
      case jval: JValue => {
        (jval \ "name") match {
          case JString("VectorSimilarityFeatureExtractor") => {
            new VectorSimilarityFeatureExtractor(jval, fileUtil)
          }
          case JString("PraFeatureExtractor") => new PraFeatureExtractor(jval)
          case JString("PraFeatureExtractorWithFilter") => new PraFeatureExtractorWithFilter(jval)
          case JString("ConnectedAtOneFeatureExtractor") => new ConnectedAtOneFeatureExtractor(jval)
          case JString("ConnectedByMediatorFeatureExtractor") => new ConnectedByMediatorFeatureExtractor(jval)
          case other => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
        }
      }
    }
  }
}

class PraFeatureExtractor(params: JValue) extends NodePairFeatureExtractor {
  // If this is set to true, we will output nodes as part of the path type when encoding features.
  // This requires that the PathTypeFactory in the PathFinder has been set to
  // LexicalizePathTypeFactory, so we have path types that actually keep the nodes around.  If
  // includeNodes is true and we don't have lexicalized path types, this will crash.
  val includeNodes = JsonHelper.extractWithDefault(params, "include nodes", false)

  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1
        val feature = if (includeNodes) {
          pathType match {
            case p: LexicalizedPathType => { p.encodeAsHumanReadableString(graph) }
            case _ => throw new IllegalStateException(
              "must have lexicalized path types to include nodes")
          }
        } else {
          pathType match {
            case p: LexicalizedPathType => { p.encodeAsHumanReadableStringWithoutNodes(graph) }
            case p => { p.encodeAsHumanReadableString(graph) }
          }
        }
        Seq(feature)
      } else {
        Seq[String]()
      }
    }).toSeq
  }

  override def getFeatureMatcher(
    feature: String,
    startFromSourceNode: Boolean,
    graph: Graph
  ): Option[FeatureMatcher[NodePairInstance]] = {
    PraFeatureMatcher.create(feature, startFromSourceNode, graph)
  }
}

class PraFeatureExtractorWithFilter(params: JValue) extends PraFeatureExtractor(params) {
  val filter = PathTypeFilter.create(params \ "filter")

  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget) && filter.shouldKeepPath(entry._1, graph)) {
        Seq(entry._1.encodeAsHumanReadableString(graph))
      } else {
        Seq[String]()
      }
    }).toSeq
  }
}

class OneSidedPathAndEndNodeFeatureExtractor(outputter: Outputter) extends NodePairFeatureExtractor {
  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.flatMap(entry => {
      entry._2.map(nodePair => {
        val (start, end) = nodePair
        val path = entry._1.encodeAsHumanReadableString(graph)
        val endNode = graph.getNodeName(end)
        if (start == instance.source) {
          "SOURCE:" + path + ":" + endNode
        } else if (start == instance.target) {
          "TARGET:" + path + ":" + endNode
        } else {
          outputter.fatal(s"Source: ${instance.source}")
          outputter.fatal(s"Target: ${instance.target}")
          outputter.fatal(s"Left node: ${start}")
          outputter.fatal(s"Right node: ${end}")
          outputter.fatal(s"path: ${path}")
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be either the source or the target")
        }
      })
    }).toSeq
  }
}

class OneSidedPathOnlyFeatureExtractor(outputter: Outputter) extends NodePairFeatureExtractor {
  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.flatMap(entry => {
      entry._2.map(nodePair => {
        val (start, end) = nodePair
        val path = entry._1.encodeAsHumanReadableString(graph)
        val endNode = graph.getNodeName(end)
        if (start == instance.source) {
          "SOURCE:" + path
        } else if (start == instance.target) {
          "TARGET:" + path
        } else {
          outputter.fatal(s"Source: ${instance.source}")
          outputter.fatal(s"Target: ${instance.target}")
          outputter.fatal(s"Left node: ${start}")
          outputter.fatal(s"Right node: ${end}")
          outputter.fatal(s"path: ${path}")
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be either the source or the target")
        }
      })
    }).toSeq
  }
}

class CategoricalComparisonFeatureExtractor extends NodePairFeatureExtractor{
  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(graph)
      val (src, targ) = entry._2.partition(nodePair => nodePair._1 == instance.source)
      val pairs = for (int1 <- src; int2 <- targ)
        yield (graph.getNodeName(int1._2), graph.getNodeName(int2._2));
      for{pair <- pairs}  yield "CATCOMP:" + path + ":" + pair._1 + ":" + pair._2
    }).toSeq
  }
}

class NumericalComparisonFeatureExtractor extends NodePairFeatureExtractor{
  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(graph)
      val (src, targ) = entry._2.partition(nodePair => nodePair._1 == instance.source)
      val strings = for {int1 <- src; int2 <- targ}
        yield (graph.getNodeName(int1._2), graph.getNodeName(int2._2))
      val valid_strings = strings.filter(str => isDoubleNumber(str._1) && isDoubleNumber(str._2))
      for(str <- valid_strings )
        yield s"NUMCOMP:${path}:" + "%.2f".format(log10(abs(str._1.toDouble - str._2.toDouble))).toDouble

    }).toSeq
  }

  def isDoubleNumber(s: String): Boolean = (allCatch opt s.toDouble).isDefined
}

class PathBigramsFeatureExtractor extends NodePairFeatureExtractor {
  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        List(entry._1.encodeAsHumanReadableString(graph))
        val pathType = entry._1.asInstanceOf[BaseEdgeSequencePathType]
        val edgeTypes = pathType.getEdgeTypes()
        val reverses = pathType.getReverse()
        val edgeTypeStrings = "@START@" +: edgeTypes.zip(reverses).map(edge => {
          val edgeString = graph.getEdgeName(edge._1)
          if (edge._2) "_" + edgeString else edgeString
        }).toList :+ "@END@"
        val bigrams = for (i <- (1 until edgeTypeStrings.size))
            yield "BIGRAM:" + edgeTypeStrings(i-1) + "-" + edgeTypeStrings(i)
        bigrams
      } else {
        List[String]()
      }
    }).toSeq
  }
}

class VectorSimilarityFeatureExtractor(
    val params: JValue,
    fileUtil: FileUtil = new FileUtil) extends NodePairFeatureExtractor{
  implicit val formats = DefaultFormats

  val allowedParamKeys = Seq("name", "matrix path", "max similar vectors")
  JsonHelper.ensureNoExtras(params, "VectorSimilarityFeatureExtractor", allowedParamKeys)
  val matrixPath = (params \ "matrix path").extract[String]
  val maxSimilarVectors = JsonHelper.extractWithDefault(params, "max similar vectors", 10)
  // build similarity matrix in memory
  val lines = fileUtil.readLinesFromFile(matrixPath)
  val pairs = lines.map(line => {
    val words = line.split("\t")
    (words(0), (words(1), words(2).toDouble))
  }).toList.sorted
  val relations = pairs.groupBy(_._1).mapValues(_.map(_._2).sortBy(-_._2).take(maxSimilarVectors).map(_._1))
  val ANY_REL = "@ANY_REL@"

  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val (anyRel: Int, edgeMap: Map[Int, String]) = {
      if (graph.hasEdge(ANY_REL)) {
        (graph.getEdgeIndex(ANY_REL), Map())
      } else {
        val index = graph.getNumEdgeTypes + 100
        (index, Map(index -> ANY_REL))
      }
    }
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1.asInstanceOf[BaseEdgeSequencePathType]
        val edgeTypes = pathType.getEdgeTypes()
        val reverses = pathType.getReverse()
        val similarities =
          for (i <- (0 until edgeTypes.length);
               relStr = graph.getEdgeName(edgeTypes(i));
               similar <- (Seq(edgeTypes(i), anyRel) ++
                 relations.getOrElse(relStr, Seq()).map(r => graph.getEdgeIndex(r))))
            yield (i, similar)
        similarities.map(sim => {
          val oldEdgeType = edgeTypes(sim._1)
          edgeTypes(sim._1) = sim._2
          val similar = new BasicPathTypeFactory.BasicPathType(edgeTypes, reverses)
            .encodeAsHumanReadableString(graph, edgeMap)
          edgeTypes(sim._1) = oldEdgeType
          "VECSIM:" + similar
        }).toSet
      } else {
        List[String]()
      }
    }).toSeq
  }
}

class AnyRelFeatureExtractor extends NodePairFeatureExtractor{

  val ANY_REL = "@ANY_REL@"

  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val (anyRel: Int, edgeMap: Map[Int, String]) = {
      if (graph.hasEdge(ANY_REL)) {
        (graph.getEdgeIndex(ANY_REL), Map())
      } else {
        val index = graph.getNumEdgeTypes + 100
        (index, Map(index -> ANY_REL))
      }
    }
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1.asInstanceOf[BaseEdgeSequencePathType]
        val edgeTypes = pathType.getEdgeTypes()
        val reverses = pathType.getReverse()
        (0 until edgeTypes.length).map(i => {
          val oldEdgeType = edgeTypes(i)
          edgeTypes(i) = anyRel
          val newPathType = new BasicPathTypeFactory.BasicPathType(edgeTypes, reverses)
            .encodeAsHumanReadableString(graph, edgeMap)
          edgeTypes(i) = oldEdgeType
          "ANYREL:" + newPathType
        }).toSet
      } else {
        List[String]()
      }
    }).toSeq
  }
}

class AnyRelAliasOnlyFeatureExtractor extends NodePairFeatureExtractor{

  val ANY_REL = "@ANY_REL@"
  val ALIAS = "@ALIAS@"

  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph): Seq[String] = {
    val graph = instance.graph
    if (!graph.hasEdge("@ALIAS@")) {
      return List[String]()
    }
    val (anyRel: Int, edgeMap: Map[Int, String]) = {
      if (graph.hasEdge(ANY_REL)) {
        (graph.getEdgeIndex(ANY_REL), Map())
      } else {
        val index = graph.getNumEdgeTypes + 100
        (index, Map(index -> ANY_REL))
      }
    }
    // TODO(matt): this is brittle, because the alias relation might have a different name...
    val alias = graph.getEdgeIndex("@ALIAS@")
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1.asInstanceOf[BaseEdgeSequencePathType]
        val edgeTypes = pathType.getEdgeTypes()
        if (edgeTypes(0) == alias && edgeTypes(edgeTypes.length - 1) == alias) {
          val reverses = pathType.getReverse()
          (1 until edgeTypes.length - 1).map(i => {
            val oldEdgeType = edgeTypes(i)
            edgeTypes(i) = anyRel
            val newPathType = new BasicPathTypeFactory.BasicPathType(edgeTypes, reverses)
              .encodeAsHumanReadableString(graph, edgeMap)
            edgeTypes(i) = oldEdgeType
            "ANYREL:" + newPathType
          }).toSet
        } else {
          List[String]()
        }
      } else {
        List[String]()
      }
    }).toSeq
  }
}

class ConnectedAtOneFeatureExtractor(params: JValue) extends NodePairFeatureExtractor {
  val featureName = JsonHelper.extractWithDefault(params, "feature name", "CONNECTED")

  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1
        val numSteps = pathType match {
          case p: BaseEdgeSequencePathType => { p.edgeTypes.length }
          case p: LexicalizedPathType => { p.edgeTypes.length }
          case _ => throw new IllegalStateException("what kind of path type are you using?")
        }
        if (numSteps == 1) {
          Seq(featureName)
        } else {
          Seq[String]()
        }
      } else {
        Seq[String]()
      }
    }).toSet.toSeq
  }

  override def getFeatureMatcher(
    feature: String,
    startFromSourceNode: Boolean,
    graph: Graph
  ): Option[FeatureMatcher[NodePairInstance]] = {
    if (feature == featureName) {
      Some(ConnectedAtOneMatcher)
    } else {
      None
    }
  }
}

class ConnectedByMediatorFeatureExtractor(params: JValue) extends NodePairFeatureExtractor {
  val featureName = JsonHelper.extractWithDefault(params, "feature name", "CONNECTED")
  lazy val stream = getClass().getResourceAsStream("/freebase_mediator_relations.tsv")
  lazy val mediators = new FileUtil().getLineIterator(stream).toSet

  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1
        val edges = pathType match {
          case p: BaseEdgeSequencePathType => { p.edgeTypes }
          case p: LexicalizedPathType => { p.edgeTypes }
          case _ => throw new IllegalStateException("what kind of path type are you using?")
        }
        if (edges.length == 2) {
          val firstEdgeName = graph.getEdgeName(edges(0))
          val secondEdgeName = graph.getEdgeName(edges(1))
          if (mediators.contains(firstEdgeName) && mediators.contains(secondEdgeName)) {
            Seq(featureName)
          } else {
            Seq[String]()
          }
        } else {
          Seq[String]()
        }
      } else {
        Seq[String]()
      }
    }).toSet.toSeq
  }

  override def getFeatureMatcher(
    feature: String,
    startFromSourceNode: Boolean,
    graph: Graph
  ): Option[FeatureMatcher[NodePairInstance]] = {
    if (feature == featureName) {
      Some(new ConnectedByMediatorMatcher(mediators.flatMap(m => {
        if (graph.hasEdge(m)) {
          Seq(graph.getEdgeIndex(m))
        } else {
          Seq()
        }
      }).toSet))
    } else {
      None
    }
  }
}
