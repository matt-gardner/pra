package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.Pair
import com.mattg.util.Vector

import java.io.File

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.json4s._
import org.json4s.Formats
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL.WithDouble._

abstract class SubgraphFeatureGenerator[T <: Instance](
  params: JValue,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil()
) extends FeatureGenerator[T] {
  implicit val formats = DefaultFormats
  val featureParamKeys = Seq("type", "path finder", "feature extractors", "feature size",
    "include bias", "log level")
  JsonHelper.ensureNoExtras(params, "operation -> features", featureParamKeys)

  val featureDict = new MutableConcurrentDictionary
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
      Some(createMatrixRow(instance, features.toSet.map(hashFeature).toSeq.sorted))
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
  fileUtil: FileUtil = new FileUtil()
) extends SubgraphFeatureGenerator[NodePairInstance](params, outputter, fileUtil) {
  def createExtractors(params: JValue): Seq[FeatureExtractor[NodePairInstance]] = {
    val extractorNames: List[JValue] = JsonHelper.extractWithDefault(params, "feature extractors",
      List(JString("PraFeatureExtractor").asInstanceOf[JValue]))
    extractorNames.map(params => NodePairFeatureExtractor.create(params, outputter, fileUtil))
  }

  def createPathFinder() = NodePairPathFinder.create(params \ "path finder", relation,
    relationMetadata, outputter, fileUtil)
}

class NodeSubgraphFeatureGenerator(
  params: JValue,
  relation: String,
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil()
) extends SubgraphFeatureGenerator[NodeInstance](params, outputter, fileUtil) {
  def createExtractors(params: JValue): Seq[FeatureExtractor[NodeInstance]] = {
    val extractorNames: List[JValue] = JsonHelper.extractWithDefault(params, "feature extractors",
      List(JString("PathOnlyFeatureExtractor").asInstanceOf[JValue]))
    extractorNames.map(params => NodeFeatureExtractor.create(params, outputter, fileUtil))
  }

  def createPathFinder() =
    NodePathFinder.create(params \ "path finder", relation, relationMetadata, outputter)
}
