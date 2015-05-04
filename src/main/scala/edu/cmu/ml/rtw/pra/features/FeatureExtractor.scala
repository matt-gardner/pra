package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.pra.config.JsonHelper

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
  def extractFeatures(source: Int, target: Int, subgraph: Subgraph): java.util.List[String]
}

class PraFeatureExtractor(val edgeDict: Dictionary)  extends FeatureExtractor {
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    val sourceTarget = new Pair[Integer, Integer](source, target)
    subgraph.asScala.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        List(entry._1.encodeAsHumanReadableString(edgeDict))
      } else {
        List[String]()
      }
    }).toList.asJava
  }
}

class OneSidedFeatureExtractor(val edgeDict: Dictionary, val nodeDict: Dictionary) extends FeatureExtractor {
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    subgraph.asScala.flatMap(entry => {
      entry._2.asScala.map(nodePair => {
        val path = entry._1.encodeAsHumanReadableString(edgeDict)
        val endNode = nodeDict.getString(nodePair.getRight)
        if (nodePair.getLeft == source) {
          "SOURCE:" + path + ":" + endNode
        } else if (nodePair.getLeft == target) {
          "TARGET:" + path + ":" + endNode
        } else {
          println(s"Source: ${source}")
          println(s"Target: ${target}")
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

class CategoricalComparisonFeatureExtractor(val edgeDict: Dictionary, val nodeDict: Dictionary) extends FeatureExtractor{
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    subgraph.asScala.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(edgeDict)
      val (src, targ) = entry._2.asScala.partition(nodePair => nodePair.getLeft == source)
      val pairs = for (int1 <- src; int2 <- targ) yield (nodeDict.getString(int1.getRight), nodeDict.getString(int2.getRight));
      for{pair <- pairs}  yield "CATCOMP:" + path + ":" + pair._1 + ":" + pair._2
    }).toList.asJava
  }
}

class NumericalComparisonFeatureExtractor(val edgeDict: Dictionary, val nodeDict: Dictionary) extends FeatureExtractor{
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    subgraph.asScala.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(edgeDict)
      val (src, targ) = entry._2.asScala.partition(nodePair => nodePair.getLeft == source)
      val strings = for {int1 <- src; int2 <- targ}
        yield (nodeDict.getString(int1.getRight), nodeDict.getString(int2.getRight))
      val valid_strings = strings.filter(str => isDoubleNumber(str._1) && isDoubleNumber(str._2))
      for(str <- valid_strings )
        yield s"NUMCOMP:${path}:" + "%.2f".format(log10(abs(str._1.toDouble - str._2.toDouble))).toDouble

    }).toList.asJava
  }

  def isDoubleNumber(s: String): Boolean = (allCatch opt s.toDouble).isDefined
}


class VectorSimilarityFeatureExtractor(
    val edgeDict: Dictionary,
    val nodeDict: Dictionary,
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
    (edgeDict.getIndex(words(0)), (edgeDict.getIndex(words(1)), words(2).toDouble))
  }).toList.sorted
  val relations = pairs.groupBy(_._1).mapValues(_.map(_._2).sortBy(-_._2).take(maxSimilarVectors).map(_._1))
  val anyRel = edgeDict.getIndex("@ANY_REL@")

  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    val sourceTarget = new Pair[Integer, Integer](source, target)
    subgraph.asScala.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        val pathType = entry._1.asInstanceOf[BaseEdgeSequencePathType]
        val edgeTypes = pathType.getEdgeTypes()
        val reverses = pathType.getReverse()
        val similarities =
          for (i <- (0 until edgeTypes.length);
          similar <- (Seq(edgeTypes(i), anyRel) ++ relations.getOrElse(edgeTypes(i), Seq())))
            yield (i, similar)
        similarities.map(sim => {
          val oldEdgeType = edgeTypes(sim._1)
          edgeTypes(sim._1) = sim._2
          val similar = new BasicPathTypeFactory.BasicPathType(edgeTypes, reverses)
            .encodeAsHumanReadableString(edgeDict)
          edgeTypes(sim._1) = oldEdgeType
          "VECSIM:" + similar
        }).toSet
      } else {
        List[String]()
      }
    }).toList.asJava
  }
}
