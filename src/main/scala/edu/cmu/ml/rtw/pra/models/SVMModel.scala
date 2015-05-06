package edu.cmu.ml.rtw.pra.models

import cc.mallet.pipe.Noop
import cc.mallet.pipe.Pipe
import cc.mallet.pipe.Target2Label
import cc.mallet.pipe.SerialPipes
import cc.mallet.types.Alphabet
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

import edu.cmu.ml.rtw.pra.models.mallet_svm.common.SparseVector
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.CustomKernel
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.LinearKernel
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.TreeKernel
import edu.cmu.ml.rtw.pra.models.mallet_svm.kernel.RBFKernel
import edu.cmu.ml.rtw.pra.models.mallet_svm.SVMClassifierTrainer
import edu.cmu.ml.rtw.pra.models.mallet_svm.SVMClassifier
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_model
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_node
import edu.cmu.ml.rtw.pra.models.mallet_svm.libsvm.svm_parameter

class SVMModel(config: PraConfig, params: JValue)
    extends PraModel(config, JsonHelper.extractWithDefault(params, "binarize features", false)) {
  implicit val formats = DefaultFormats
  val allowedParams = Seq("type", "binarize features", "kernel")
  JsonHelper.ensureNoExtras(params, "pra parameters -> learning", allowedParams)

  val kernel = createKernel()

  // initializes to an empty sequence
  var svmClassifier: SVMClassifier = null
  var alphabet: Alphabet = null

  def createKernel(): CustomKernel = (params \ "kernel") match {
    case JString("linear") => new LinearKernel()
    case JString("quadratic") => new CustomKernel() {
      override def evaluate(x: svm_node, y: svm_node): Double = {
        val dotProduct = x.data.asInstanceOf[SparseVector] dot y.data.asInstanceOf[SparseVector]
        dotProduct * dotProduct
      }
    }
    case jval: JValue => {
      (jval \ "type") match {
        case JString("rbf") => {
          val param = new svm_parameter()
          param.gamma = (jval \ "gamma").extract[Double]
          new RBFKernel(param)
        }
        case other => throw new IllegalStateException("Unrecognized kernel")
      }
    }
    case other => throw new IllegalStateException("Unrecognized kernel")
  }

  /**
   * Given a feature matrix and a list of sources and targets that determines whether an
   * instances is positive or negative, train an SVM.
   */
  override def train(featureMatrix: FeatureMatrix, dataset: Dataset, featureNames: Seq[String]) = {
    println("Learning feature weights")
    println("Prepping training data")

    println("Creating alphabet")
    val pipes = new mutable.ArrayBuffer[Pipe]
    pipes.+=(new Noop())
    pipes.+=(new Target2Label())
    val pipe = new SerialPipes(pipes.asJava)
    val data = new InstanceList(pipe)
    alphabet = new Alphabet(featureNames.asJava.toArray())

    convertFeatureMatrixToMallet(featureMatrix, dataset, featureNames, data, alphabet)

    println("Creating the MalletLibSVM object")
    val svmTrainer = new SVMClassifierTrainer(kernel)

    // Finally, we train.  All that prep and everything that follows is really just to get
    // ready for and pass on the output of this one line.
    svmClassifier = svmTrainer.train(data)
  }

  /**
   * Compute score for matrix row according to learned parameters and support vectors
   * which are stored in the svmClassifier
   */
  override def classifyMatrixRow(row: MatrixRow) = {
    svmClassifier.scoreInstance(matrixRowToInstance(row, alphabet, true))
  }
}
