package edu.cmu.ml.rtw.pra.models

import cc.mallet.types.Alphabet
import cc.mallet.types.FeatureVector
import cc.mallet.types.{Instance => MalletInstance}
import cc.mallet.types.InstanceList

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Handles learning and classification for models that do batch training.
 *
 * Note that this only deals with _feature indices_, and has no concept of path types or anything
 * else.  So you need to be sure that the feature indices don't change between training and
 * classification time, or your model will be all messed up.
 */

abstract class BatchModel(config: PraConfig, binarizeFeatures: Boolean) {
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

    Outputter.info("Separating into positive, negative, unseen")
    val grouped = featureMatrix.getRows().asScala.groupBy(row => {
      if (row.instance.isPositive == true)
        "positive"
      else if (row.instance.isPositive == false)
        "negative"
      else
        // TODO(matt): I've removed this possibility from the code, migrating towards fixed
        // training and test sets...  I should add it back in, but that will be later.  The right
        // way to do that is probably by making isPositive an Option[Boolean]
        "unseen"
    })
    val positiveMatrix = new FeatureMatrix(grouped.getOrElse("positive", Seq()).asJava)
    val negativeMatrix = new FeatureMatrix(grouped.getOrElse("negative", Seq()).asJava)
    val unseenMatrix = new FeatureMatrix(grouped.getOrElse("unseen", Seq()).asJava)
    // TODO(matt): this shouldn't have any checks on it, because those should be inside
    // config.outputter.  And it'd be nice to figure out how to get rid of the feature names
    // parameter to this method, but we'll worry about that later...
    if (config.outputMatrices && config.outputBase != null) {
      Outputter.info("Outputting matrices")
      val base = config.outputBase
      config.outputter.outputFeatureMatrix(s"${base}positive_matrix.tsv", positiveMatrix, featureNames)
      config.outputter.outputFeatureMatrix(s"${base}negative_matrix.tsv", negativeMatrix, featureNames)
      config.outputter.outputFeatureMatrix(s"${base}unseen_matrix.tsv", unseenMatrix, featureNames)
    }

    Outputter.info("Converting positive matrix to MALLET instances and adding to the dataset")
    // First convert the positive matrix to a scala object
    positiveMatrix.getRows().asScala
    // Then, in parallel, map the MatrixRow objects there to MALLET Instance objects
      .par.map(row => matrixRowToInstance(row, alphabet))
    // Then, sequentially, add them to the data object, and simultaneously count how many columns
    // there are.
      .seq.foreach(instance => {
        data.addThruPipe(instance)
      })

    Outputter.info("Adding negative evidence")
    val numPositiveFeatures = positiveMatrix.getRows().asScala.map(_.columns).sum
    var numNegativeFeatures = 0
    for (negativeExample <- negativeMatrix.getRows().asScala) {
      numNegativeFeatures += negativeExample.columns
      data.addThruPipe(matrixRowToInstance(negativeExample, alphabet))
    }
    Outputter.info("Number of positive features: " + numPositiveFeatures)
    Outputter.info("Number of negative features: " + numNegativeFeatures)
    if (numNegativeFeatures < numPositiveFeatures) {
      Outputter.info("Using unseen examples to make up the difference")
      val difference = numPositiveFeatures - numNegativeFeatures
      var numUnseenFeatures = 0.0
      for (unseenExample <- unseenMatrix.getRows().asScala) {
        numUnseenFeatures += unseenExample.columns
      }
      Outputter.info("Number of unseen features: " + numUnseenFeatures)
      val unseenWeight = difference / numUnseenFeatures
      Outputter.info("Unseen weight: " + unseenWeight)
      for (unseenExample <- unseenMatrix.getRows().asScala) {
        val unseenInstance = matrixRowToInstance(unseenExample, alphabet)
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
  def classifyInstances(featureMatrix: FeatureMatrix): Seq[(Instance, Double)] = {
    Outputter.info("Classifying instances")
    featureMatrix.getRows().asScala.map(matrixRow => {
      val score = classifyMatrixRow(matrixRow)
      (matrixRow.instance, score)
    })
  }

  protected def classifyMatrixRow(row: MatrixRow): Double

  def matrixRowToInstance(row: MatrixRow, alphabet: Alphabet): MalletInstance = {
    val value = if (row.instance.isPositive) 1.0 else 0.0
    val rowValues = row.values.map(v => if (binarizeFeatures) 1 else v)
    val feature_vector = new FeatureVector(alphabet, row.featureTypes, rowValues)
    new MalletInstance(feature_vector, value, row.instance.source + " " + row.instance.target, null)
  }
}

object BatchModel{
  def create(params: JValue, config: PraConfig): BatchModel = {
    val modelType = JsonHelper.extractWithDefault(params, "type", "logistic regression")
    modelType match {
      case "logistic regression" => new LogisticRegressionModel(config, params)
      case "svm" => new SVMModel(config, params)
      case other => throw new IllegalStateException("Unrecognized model type")
    }
  }
}
