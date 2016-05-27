package edu.cmu.ml.rtw.pra.models

import cc.mallet.pipe.Noop
import cc.mallet.types.Alphabet
import cc.mallet.types.InstanceList

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow

import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.MutableConcurrentDictionary

import scala.collection.JavaConverters._
import scala.collection.mutable

class LogisticRegressionModel[T <: Instance](
  params: JValue,
  outputter: Outputter
) extends BatchModel[T](
  JsonHelper.extractAsOption[Int](params, "max training examples"),
  JsonHelper.extractWithDefault(params, "binarize features", false),
  outputter,
  JsonHelper.extractWithDefault(params, "log level", 3)
) {
  val allowedParams = Seq("type", "l1 weight", "l2 weight", "binarize features", "log level",
    "max training examples")
  JsonHelper.ensureNoExtras(params, "operation -> learning", allowedParams)

  val l1Weight = JsonHelper.extractWithDefault(params, "l1 weight", 0.0)

  val l2Weight = JsonHelper.extractWithDefault(params, "l2 weight", 0.0)

  // initializes to an empty sequence
  var lrWeights: Seq[Double] = Seq()

  /**
   * Given a feature matrix and a list of sources and targets that determines whether an
   * instance is positive or negative, train a logistic regression classifier.
   */
  override def train(featureMatrix: FeatureMatrix, dataset: Dataset[T], featureNames: Seq[String]) = {
    outputter.info("Learning feature weights")
    outputter.info("Prepping training data")

    outputter.info("Creating alphabet")
    // Set up some mallet boiler plate so we can use Burr's ShellClassifier
    val pipe = new Noop()
    val data = new InstanceList(pipe)
    val alphabet = new Alphabet(featureNames.asJava.toArray())

    convertFeatureMatrixToMallet(featureMatrix, dataset, featureNames, data, alphabet)

    outputter.info("Creating the MalletLogisticRegression object")
    val lr = new MalletLogisticRegression(alphabet)
    if (l2Weight != 0.0) {
      outputter.info("Setting L2 weight to " + l2Weight)
      lr.setL2wt(l2Weight)
    }
    if (l1Weight != 0.0) {
      outputter.info("Setting L1 weight to " + l1Weight)
      lr.setL1wt(l1Weight)
    }

    // Finally, we train.  All that prep and everything that follows is really just to get
    // ready for and pass on the output of this one line.
    outputter.info("Training the classifier")
    lr.train(data)
    val features = lr.getSparseFeatures()
    val params = lr.getSparseParams()
    val bias = lr.getBias()  // TODO(matt): why did I not save this for later use?
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
    outputter.outputWeights(weights, featureNames)
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

object LogisticRegressionModel {
  def loadFromFile[T <: Instance](
    filename: String,
    dictionary: MutableConcurrentDictionary,
    outputter: Outputter,
    fileUtil: FileUtil = new FileUtil
  ): LogisticRegressionModel[T] = {
    val featuresAndWeights = fileUtil.mapLinesFromFile(filename, (line) => {
      val fields = line.split("\t")
      (fields(0), fields(1).toDouble)
    }).filter(_._2 != 0.0)
    val weights = new Array[Double](featuresAndWeights.size + dictionary.size)
    for ((feature, weight) <- featuresAndWeights) {
      val featureIndex = dictionary.getIndex(feature)
      weights(featureIndex) = weight
    }

    val model = new LogisticRegressionModel[T](JNothing, outputter)
    model.lrWeights = weights.toSeq
    model
  }
}
