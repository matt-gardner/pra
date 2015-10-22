package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.JsonHelper
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.Vector

import java.io.File

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.json4s._
import org.json4s.Formats
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL.WithDouble._

class SubgraphFeatureGenerator(
    params: JValue,
    config: PraConfig,
    fileUtil: FileUtil = new FileUtil()) extends FeatureGenerator {
  implicit val formats = DefaultFormats
  val featureParamKeys = Seq("type", "path finder", "feature extractors", "feature size",
    "include bias", "log level")
  JsonHelper.ensureNoExtras(params, "operation -> features", featureParamKeys)

  val featureDict = new Dictionary
  val featureSize = JsonHelper.extractWithDefault(params, "feature size", -1)
  val includeBias = JsonHelper.extractWithDefault(params, "include bias", false)
  val logLevel = JsonHelper.extractWithDefault(params, "log level", 3)

  lazy val pathFinder = PathFinder.create(params \ "path finder", config)

  override def constructMatrixRow(instance: Instance) =
    extractFeatures(instance, getLocalSubgraph(instance))

  override def createTrainingMatrix(data: Dataset): FeatureMatrix = createMatrixFromData(data)

  override def removeZeroWeightFeatures(weights: Seq[Double]): Seq[Double] = weights

  override def createTestMatrix(data: Dataset): FeatureMatrix = createMatrixFromData(data)

  def createMatrixFromData(data: Dataset) = {
    val subgraphs = getLocalSubgraphs(data)
    Outputter.outputAtLevel(s"Done getting subgraphs; extracting features", logLevel)
    extractFeatures(subgraphs)
  }

  override def getFeatureNames(): Array[String] = {
    // Not really sure if par is useful here...  Maybe I should just take it out.
    val features = (1 until featureDict.getNextIndex).par.map(i => {
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

  def getLocalSubgraphs(data: Dataset): Map[Instance, Subgraph] = {
    Outputter.outputAtLevel(s"Finding local subgraphs with ${data.instances.size} training instances",
      logLevel)

    val edgesToExclude = createEdgesToExclude(data.instances, config.unallowedEdges)
    pathFinder.findPaths(config, data, edgesToExclude)

    pathFinder.getLocalSubgraphs
  }

  def getLocalSubgraph(instance: Instance): Subgraph = {
    val edgesToExclude = createEdgesToExclude(Seq(instance), config.unallowedEdges)
    pathFinder.getLocalSubgraph(instance, edgesToExclude)
  }

  def extractFeatures(instance: Instance, subgraph: Subgraph): Option[MatrixRow] = {
    val features = featureExtractors.flatMap(_.extractFeatures(instance, subgraph))
    if (features.size > 0) {
      Some(createMatrixRow(instance, features.toSet.map(hashFeature).toSeq.sorted))
    } else {
      None
    }
  }

  def extractFeatures(subgraphs: Map[Instance, Subgraph]): FeatureMatrix = {
    val matrix_rows = subgraphs.par.flatMap(entry => {
      extractFeatures(entry._1, entry._2) match {
        case Some(row) => Seq(row)
        case None => Seq()
      }
    }).seq.toList
    new FeatureMatrix(matrix_rows.asJava)
  }

  def createExtractors(params: JValue): Seq[FeatureExtractor] = {
    val extractorNames: List[JValue] = JsonHelper.extractWithDefault(params, "feature extractors",
      List(JString("PraFeatureExtractor").asInstanceOf[JValue]))
    extractorNames.map(params => FeatureExtractor.create(params, config, fileUtil))
  }

  def hashFeature(feature: String): Int = {
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

  def createMatrixRow(instance: Instance, features: Seq[Int]): MatrixRow = {
    val size = if (includeBias) features.size + 1 else features.size
    val values = new Array[Double](size)
    for (i <- 0 until size) {
      values(i) = 1
    }
    val featureIndices = if (includeBias) features :+ 0 else features
    new MatrixRow(instance, featureIndices.toArray, values)
  }
}
