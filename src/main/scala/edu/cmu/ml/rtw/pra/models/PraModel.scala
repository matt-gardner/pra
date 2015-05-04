package edu.cmu.ml.rtw.pra.models

import cc.mallet.types.Alphabet
import cc.mallet.types.FeatureVector
import cc.mallet.types.Instance
import cc.mallet.types.InstanceList

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Handles learning and classification for models that uses PRA features.
 *
 * Note that this only deals with _feature indices_, and has no concept of path types or anything
 * else.  So you need to be sure that the feature indices don't change between training and
 * classification time, or your model will be all messed up.
 */

abstract class PraModel(config: PraConfig, binarizeFeatures: Boolean) {
  /**
   * Given a feature matrix and a list of sources and targets that determines whether an
   * instance is positive or negative, train a model.
   */
  def train(featureMatrix: FeatureMatrix, dataset: Dataset, featureNames: Seq[String])

  // TODO(matt): this interface could probably be cleaned up a bit.
  def convertFeatureMatrixToMallet(
      featureMatrix: FeatureMatrix,
      dataset: Dataset,
      featureNames: Seq[String],
      data: InstanceList,
      alphabet: Alphabet) {
    val knownPositives = dataset.getPositiveInstances.asScala.map(x => (x.getLeft.toInt, x.getRight.toInt)).toSet
    val knownNegatives = dataset.getNegativeInstances.asScala.map(x => (x.getLeft.toInt, x.getRight.toInt)).toSet

    println("Separating into positive, negative, unseen")
    val grouped = featureMatrix.getRows().asScala.groupBy(row => {
      val sourceTarget = (row.sourceNode.toInt, row.targetNode.toInt)
      if (knownPositives.contains(sourceTarget))
        "positive"
      else if (knownNegatives.contains(sourceTarget))
        "negative"
      else
        "unseen"
    })
    val positiveMatrix = new FeatureMatrix(grouped.getOrElse("positive", Seq()).asJava)
    val negativeMatrix = new FeatureMatrix(grouped.getOrElse("negative", Seq()).asJava)
    val unseenMatrix = new FeatureMatrix(grouped.getOrElse("unseen", Seq()).asJava)
    if (config.outputMatrices && config.outputBase != null) {
      println("Outputting matrices")
      val base = config.outputBase
      config.outputter.outputFeatureMatrix(s"${base}positive_matrix.tsv", positiveMatrix, featureNames.asJava)
      config.outputter.outputFeatureMatrix(s"${base}negative_matrix.tsv", negativeMatrix, featureNames.asJava)
      config.outputter.outputFeatureMatrix(s"${base}unseen_matrix.tsv", unseenMatrix, featureNames.asJava)
    }

    println("Converting positive matrix to MALLET instances and adding to the dataset")
    // First convert the positive matrix to a scala object
    positiveMatrix.getRows().asScala
    // Then, in parallel, map the MatrixRow objects there to MALLET Instance objects
      .par.map(row => matrixRowToInstance(row, alphabet, true))
    // Then, sequentially, add them to the data object, and simultaneously count how many columns
    // there are.
      .seq.foreach(instance => {
        data.addThruPipe(instance)
      })

    println("Adding negative evidence")
    val numPositiveFeatures = positiveMatrix.getRows().asScala.map(_.columns).sum
    var numNegativeFeatures = 0
    for (negativeExample <- negativeMatrix.getRows().asScala) {
      numNegativeFeatures += negativeExample.columns
      data.addThruPipe(matrixRowToInstance(negativeExample, alphabet, false))
    }
    println("Number of positive features: " + numPositiveFeatures)
    println("Number of negative features: " + numNegativeFeatures)
    if (numNegativeFeatures < numPositiveFeatures) {
      println("Using unseen examples to make up the difference")
      val difference = numPositiveFeatures - numNegativeFeatures
      var numUnseenFeatures = 0.0
      for (unseenExample <- unseenMatrix.getRows().asScala) {
        numUnseenFeatures += unseenExample.columns
      }
      println("Number of unseen features: " + numUnseenFeatures)
      val unseenWeight = difference / numUnseenFeatures
      println("Unseen weight: " + unseenWeight)
      for (unseenExample <- unseenMatrix.getRows().asScala) {
        val unseenInstance = matrixRowToInstance(unseenExample, alphabet, false)
        data.addThruPipe(unseenInstance)
        data.setInstanceWeight(unseenInstance, unseenWeight)
      }
    }
  }

  /**
   * Give a score to every row in the feature matrix, according to the learned weights.
   *
   * @param featureMatrix A feature matrix specified as a list of {@link MatrixRow} objects.
   *     Each row receives a score from the classifier.
   *
   * @return A map from source node to (target node, score) pairs, where the score is computed
   *     from the features in the feature matrix and the learned weights.
   */
  def classifyInstances(featureMatrix: FeatureMatrix): Map[Int, Seq[(Int, Double)]] = {
    println("Classifying instances")
    val sourceScores = new mutable.HashMap[Int, mutable.ArrayBuffer[(Int, Double)]]
    println("LR Model: size of feature matrix to classify is " + featureMatrix.size())
    for (row <- featureMatrix.getRows().asScala) {
      val score = classifyMatrixRow(row)
      sourceScores.getOrElseUpdate(row.sourceNode, new mutable.ArrayBuffer[(Int, Double)])
        .append((row.targetNode, score))
    }
    sourceScores.mapValues(_.toSeq.sortBy(x => (-x._2, x._1))).toMap
  }

  protected def classifyMatrixRow(row: MatrixRow): Double

  def matrixRowToInstance(row: MatrixRow, alphabet: Alphabet, positive: Boolean): Instance = {
    val value = if (positive) 1.0 else 0.0
    val rowValues = row.values.map(v => if (binarizeFeatures) 1 else v)
    val feature_vector = new FeatureVector(alphabet, row.pathTypes, rowValues)
    new Instance(feature_vector, value, row.sourceNode + " " + row.targetNode, null)
  }
}

object PraModelCreator {
  def create(config: PraConfig, params: JValue): PraModel = {
    val modelType = JsonHelper.extractWithDefault(params, "type", "LogisticRegressionModel")
    modelType match {
      case "LogisticRegressionModel" => new LogisticRegressionModel(config, params)
      case other => throw new IllegalStateException("Unrecognized model type")
    }
  }
}
