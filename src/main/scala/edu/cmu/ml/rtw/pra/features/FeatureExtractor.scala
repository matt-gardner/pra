package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import scala.collection.JavaConverters._
import scala.util.control.Exception.allCatch
import scala.math.log10
import scala.math.abs
import scala.io.Source

import org.json4s._
import org.json4s.Formats
import org.json4s.native.JsonMethods._

trait FeatureExtractor {
  type Subgraph = java.util.Map[PathType, java.util.Set[Pair[Integer, Integer]]]
  def extractFeatures(instance: Instance, subgraph: Subgraph): java.util.List[String]
}

class PraFeatureExtractor extends FeatureExtractor {
  override def extractFeatures(instance: Instance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = new Pair[Integer, Integer](instance.source, instance.target)
    subgraph.asScala.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        List(entry._1.encodeAsHumanReadableString(graph))
      } else {
        List[String]()
      }
    }).toList.asJava
  }
}

class OneSidedFeatureExtractor extends FeatureExtractor {
  override def extractFeatures(instance: Instance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.asScala.flatMap(entry => {
      entry._2.asScala.map(nodePair => {
        val path = entry._1.encodeAsHumanReadableString(graph)
        val endNode = graph.getNodeName(nodePair.getRight)
        if (nodePair.getLeft == instance.source) {
          "SOURCE:" + path + ":" + endNode
        } else if (nodePair.getLeft == instance.target) {
          "TARGET:" + path + ":" + endNode
        } else {
          println(s"Source: ${instance.source}")
          println(s"Target: ${instance.target}")
          println(s"Left node: ${nodePair.getLeft}")
          println(s"Right node: ${nodePair.getRight}")
          println(s"path: ${path}")
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be either the source or the target")
        }
      })
    }).toList.asJava
  }
}

class CategoricalComparisonFeatureExtractor extends FeatureExtractor{
  override def extractFeatures(instance: Instance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.asScala.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(graph)
      val (src, targ) = entry._2.asScala.partition(nodePair => nodePair.getLeft == instance.source)
      val pairs = for (int1 <- src; int2 <- targ)
        yield (graph.getNodeName(int1.getRight), graph.getNodeName(int2.getRight));
      for{pair <- pairs}  yield "CATCOMP:" + path + ":" + pair._1 + ":" + pair._2
    }).toList.asJava
  }
}

class NumericalComparisonFeatureExtractor extends FeatureExtractor{
  override def extractFeatures(instance: Instance, subgraph: Subgraph) = {
    val graph = instance.graph
    subgraph.asScala.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(graph)
      val (src, targ) = entry._2.asScala.partition(nodePair => nodePair.getLeft == instance.source)
      val strings = for {int1 <- src; int2 <- targ}
        yield (graph.getNodeName(int1.getRight), graph.getNodeName(int2.getRight))
      val valid_strings = strings.filter(str => isDoubleNumber(str._1) && isDoubleNumber(str._2))
      for(str <- valid_strings )
        yield s"NUMCOMP:${path}:" + "%.2f".format(log10(abs(str._1.toDouble - str._2.toDouble))).toDouble

    }).toList.asJava
  }

  def isDoubleNumber(s: String): Boolean = (allCatch opt s.toDouble).isDefined
}

class PathBigramsFeatureExtractor extends FeatureExtractor {
  override def extractFeatures(instance: Instance, subgraph: Subgraph) = {
    val graph = instance.graph
    val sourceTarget = new Pair[Integer, Integer](instance.source, instance.target)
    subgraph.asScala.flatMap(entry => {
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
    }).toList.asJava
  }
}

class VectorSimilarityFeatureExtractor(
    val params: JValue,
    praBase: String,
    fileUtil: FileUtil = new FileUtil) extends FeatureExtractor{
  implicit val formats = DefaultFormats

  val allowedParamKeys = Seq("name", "matrix name", "max similar vectors")
  JsonHelper.ensureNoExtras(params, "VectorSimilarityFeatureExtractor", allowedParamKeys)
  val matrixName = (params \ "matrix name").extract[String]
  val maxSimilarVectors = JsonHelper.extractWithDefault(params, "max similar vectors", 10)
  // build similarity matrix in memory
  val matrixPath = s"${praBase}embeddings/${matrixName}/matrix.tsv"
  val lines = fileUtil.readLinesFromFile(matrixPath).asScala
  val pairs = lines.map(line => {
    val words = line.split("\t")
    (words(0), (words(1), words(2).toDouble))
  }).toList.sorted
  val relations = pairs.groupBy(_._1).mapValues(_.map(_._2).sortBy(-_._2).take(maxSimilarVectors).map(_._1))

  override def extractFeatures(instance: Instance, subgraph: Subgraph) = {
    val graph = instance.graph
    val anyRel = graph.getEdgeIndex("@ANY_REL@")
    val sourceTarget = new Pair[Integer, Integer](instance.source, instance.target)
    subgraph.asScala.flatMap(entry => {
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
            .encodeAsHumanReadableString(graph)
          edgeTypes(sim._1) = oldEdgeType
          "VECSIM:" + similar
        }).toSet
      } else {
        List[String]()
      }
    }).toList.asJava
  }
}

class AnyRelFeatureExtractor extends FeatureExtractor{

  override def extractFeatures(instance: Instance, subgraph: Subgraph) = {
    val graph = instance.graph
    val anyRel = graph.getEdgeIndex("@ANY_REL@")
    val sourceTarget = new Pair[Integer, Integer](instance.source, instance.target)
    subgraph.asScala.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1.asInstanceOf[BaseEdgeSequencePathType]
        val edgeTypes = pathType.getEdgeTypes()
        val reverses = pathType.getReverse()
        (0 until edgeTypes.length).map(i => {
          val oldEdgeType = edgeTypes(i)
          edgeTypes(i) = anyRel
          val newPathType = new BasicPathTypeFactory.BasicPathType(edgeTypes, reverses)
            .encodeAsHumanReadableString(graph)
          edgeTypes(i) = oldEdgeType
          "ANYREL:" + newPathType
        }).toSet
      } else {
        List[String]()
      }
    }).toList.asJava
  }
}
