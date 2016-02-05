package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.BaseEdgeSequencePathType
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.Subgraph
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import scala.util.control.Exception.allCatch
import scala.math.log10
import scala.math.abs

import org.json4s._

trait NodePairFeatureExtractor extends FeatureExtractor[NodePairInstance] {

  // TODO(matt): implement this for all possible extractors.
  override def getFeatureMatcher(
    feature: String,
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
      case JString("PraFeatureExtractor") => new PraFeatureExtractor
      case JString("PathBigramsFeatureExtractor") => new PathBigramsFeatureExtractor
      case JString("OneSidedFeatureExtractor") => new OneSidedFeatureExtractor(outputter)
      case JString("CategoricalComparisonFeatureExtractor") => new CategoricalComparisonFeatureExtractor
      case JString("NumericalComparisonFeatureExtractor") => new NumericalComparisonFeatureExtractor
      case JString("AnyRelFeatureExtractor") => new AnyRelFeatureExtractor
      case JString("AnyRelAliasOnlyFeatureExtractor") => new AnyRelAliasOnlyFeatureExtractor
      case JString(other) => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
      case jval: JValue => {
        (jval \ "name") match {
          case JString("VectorSimilarityFeatureExtractor") => {
            new VectorSimilarityFeatureExtractor(jval, fileUtil)
          }
          case JString("PraFeatureExtractorWithFilter") => new PraFeatureExtractorWithFilter(jval)
          case other => throw new IllegalStateException(s"Unrecognized feature extractor: $other")
        }
      }
    }
  }
}

class PraFeatureExtractor extends NodePairFeatureExtractor {
  override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = (instance.source, instance.target)
    subgraph.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        Seq(entry._1.encodeAsHumanReadableString(graph))
      } else {
        Seq[String]()
      }
    }).toSeq
  }

  override def getFeatureMatcher(feature: String, graph: Graph) = {
    Some(new FeatureMatcher[NodePairInstance] {
      override def isFinished(stepsTaken: Int): Boolean = {
      }
      override def edgeOk(edgeId: Int, stepsTaken: Int): Boolean = {
      }
      override def nodeOk(nodeId: Int, stepsTaken: Int): Boolean = {
      }
    })
  }
}

class PraFeatureExtractorWithFilter(params: JValue) extends NodePairFeatureExtractor {
  val filter = PathTypeFilterCreator.create(params \ "filter")

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

class OneSidedFeatureExtractor(outputter: Outputter) extends NodePairFeatureExtractor {
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
