package edu.cmu.ml.rtw.pra.models

import cc.mallet.types.Alphabet
import cc.mallet.types.FeatureVector
import cc.mallet.types.{Instance => MalletInstance}
import cc.mallet.types.InstanceList

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.Split
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import com.mattg.util.JsonHelper

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Random

/**
 * Handles learning and classification for models that do batch training.
 *
 * Note that this only deals with _feature indices_, and has no concept of path types or anything
 * else.  So you need to be sure that the feature indices don't change between training and
 * classification time, or your model will be all messed up.
 */

abstract class BatchModel[T <: Instance](
  maxTrainingExamples: Option[Int],
  binarizeFeatures: Boolean,
  outputter: Outputter,
  logLevel: Int
) {
  /**
   * Given a feature matrix and a list of sources and targets that determines whether an
   * instance is positive or negative, train a model.
   */
  def train(featureMatrix: FeatureMatrix, dataset: Dataset[T], featureNames: Seq[String])

  // TODO(matt): this interface could probably be cleaned up a bit.
  def convertFeatureMatrixToMallet(
    featureMatrix: FeatureMatrix,
    dataset: Dataset[T],
    featureNames: Seq[String],
    data: InstanceList,
    alphabet: Alphabet
  ) {
    val keptRows = maxTrainingExamples match {
      case None => featureMatrix.getRows().asScala
      case Some(max) => Random.shuffle(featureMatrix.getRows().asScala).take(max)
    }
    outputter.info("Separating into positive, negative, unseen")
    val grouped = keptRows.groupBy(row => {
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

    outputter.info("Converting positive matrix to MALLET instances and adding to the dataset")
    // First convert the positive matrix to a scala object
    positiveMatrix.getRows().asScala
    // Then, in parallel, map the MatrixRow objects there to MALLET Instance objects
      .par.map(row => matrixRowToInstance(row, alphabet))
    // Then, sequentially, add them to the data object, and simultaneously count how many columns
    // there are.
      .seq.foreach(instance => {
        data.addThruPipe(instance)
      })

    outputter.info("Adding negative evidence")
    val numPositiveFeatures = positiveMatrix.getRows().asScala.map(_.columns).sum
    var numNegativeFeatures = 0
    for (negativeExample <- negativeMatrix.getRows().asScala) {
      numNegativeFeatures += negativeExample.columns
      data.addThruPipe(matrixRowToInstance(negativeExample, alphabet))
    }
    outputter.info("Number of positive features: " + numPositiveFeatures)
    outputter.info("Number of negative features: " + numNegativeFeatures)
    if (numNegativeFeatures < numPositiveFeatures) {
      outputter.info("Using unseen examples to make up the difference")
      val difference = numPositiveFeatures - numNegativeFeatures
      var numUnseenFeatures = 0.0
      for (unseenExample <- unseenMatrix.getRows().asScala) {
        numUnseenFeatures += unseenExample.columns
      }
      outputter.info("Number of unseen features: " + numUnseenFeatures)
      val unseenWeight = difference / numUnseenFeatures
      outputter.info("Unseen weight: " + unseenWeight)
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
  def classifyInstances(featureMatrix: FeatureMatrix): Seq[(T, Double)] = {
    outputter.outputAtLevel("Classifying instances", logLevel)
    featureMatrix.getRows().asScala.map(matrixRow => {
      val score = classifyMatrixRow(matrixRow)
      (matrixRow.instance.asInstanceOf[T], score)
    })
  }

  protected def classifyMatrixRow(row: MatrixRow): Double

  def matrixRowToInstance(row: MatrixRow, alphabet: Alphabet): MalletInstance = {
    val value = if (row.instance.isPositive) 1.0 else 0.0
    val rowValues = row.values.map(v => if (binarizeFeatures) 1 else v)
    val feature_vector = new FeatureVector(alphabet, row.featureTypes, rowValues)
    new MalletInstance(feature_vector, value, row.instance.stringKey(), null)
  }
}

object BatchModel{
  // The Split object is necessary here to nail down a specific type.
  def create[T <: Instance](params: JValue, split: Split[T], outputter: Outputter): BatchModel[T] = {
    val modelType = JsonHelper.extractWithDefault(params, "type", "logistic regression")
    modelType match {
      case "logistic regression" => new LogisticRegressionModel[T](params, outputter)
      case "svm" => new SVMModel[T](params, outputter)
      case other => throw new IllegalStateException("Unrecognized model type")
    }
  }
}
