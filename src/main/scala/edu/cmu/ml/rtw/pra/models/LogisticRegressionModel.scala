package edu.cmu.ml.rtw.pra.models

import cc.mallet.pipe.Noop
import cc.mallet.types.Alphabet
import cc.mallet.types.InstanceList

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import scala.collection.JavaConverters._
import scala.collection.mutable

class LogisticRegressionModel(config: PraConfig, params: JValue)
    extends BatchModel(config, JsonHelper.extractWithDefault(params, "binarize features", false)) {
  val allowedParams = Seq("type", "l1 weight", "l2 weight", "binarize features")
  JsonHelper.ensureNoExtras(params, "operation -> learning", allowedParams)

  val l1Weight = JsonHelper.extractWithDefault(params, "l1 weight", 0.0)

  val l2Weight = JsonHelper.extractWithDefault(params, "l2 weight", 0.0)

  // initializes to an empty sequence
  var lrWeights: Seq[Double] = Seq()

  /**
   * Given a feature matrix and a list of sources and targets that determines whether an
   * instances is positive or negative, train a logistic regression classifier.
   */
  override def train(featureMatrix: FeatureMatrix, dataset: Dataset, featureNames: Seq[String]) = {
    Outputter.info("Learning feature weights")
    Outputter.info("Prepping training data")

    Outputter.info("Creating alphabet")
    // Set up some mallet boiler plate so we can use Burr's ShellClassifier
    val pipe = new Noop()
    val data = new InstanceList(pipe)
    val alphabet = new Alphabet(featureNames.asJava.toArray())

    convertFeatureMatrixToMallet(featureMatrix, dataset, featureNames, data, alphabet)

    Outputter.info("Creating the MalletLogisticRegression object")
    val lr = new MalletLogisticRegression(alphabet)
    if (l2Weight != 0.0) {
      Outputter.info("Setting L2 weight to " + l2Weight)
      lr.setL2wt(l2Weight)
    }
    if (l1Weight != 0.0) {
      Outputter.info("Setting L1 weight to " + l1Weight)
      lr.setL1wt(l1Weight)
    }

    // Finally, we train.  All that prep and everything that follows is really just to get
    // ready for and pass on the output of this one line.
    Outputter.info("Training the classifier")
    lr.train(data)
    val features = lr.getSparseFeatures()
    val params = lr.getSparseParams()
    val bias = lr.getBias()
    val weights = new mutable.ArrayBuffer[Double]()
    var j = 0
    for (i <- 0 until featureNames.size) {
      if (j >= features.length) {
        weights += 0.0
      } else if (features(j) > i) {
        weights += 0.0
      } else if (features(j) == i) {
        weights += params(j)
        j += 1
      }
    }
    Outputter.info("Outputting feature weights")
    if (config.outputBase != null) {
      config.outputter.outputWeights(config.outputBase + "weights.tsv", weights, featureNames)
    }
    lrWeights = weights.toSeq
  }

  override def classifyMatrixRow(row: MatrixRow) = {
    val features = row.values.zip(row.featureTypes)
    features.map(f => {
      if (f._2 < lrWeights.size)
        f._1 * lrWeights(f._2)
      else
        0.0
    }).sum
  }
}
