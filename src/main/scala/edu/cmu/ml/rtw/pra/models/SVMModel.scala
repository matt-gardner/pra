package edu.cmu.ml.rtw.pra.models

import cc.mallet.pipe.Noop
import cc.mallet.pipe.Pipe
import cc.mallet.pipe.Target2Label
import cc.mallet.pipe.SerialPipes
import cc.mallet.types.Alphabet
import cc.mallet.types.InstanceList

import org.json4s._
import org.json4s.native.JsonMethods._

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import com.mattg.util.JsonHelper

import scala.collection.JavaConverters._
import scala.collection.mutable

import ca.uwo.csd.ai.nlp.common.SparseVector
import ca.uwo.csd.ai.nlp.kernel.CustomKernel
import ca.uwo.csd.ai.nlp.kernel.LinearKernel
import ca.uwo.csd.ai.nlp.kernel.TreeKernel
import ca.uwo.csd.ai.nlp.kernel.RBFKernel
import ca.uwo.csd.ai.nlp.SVMClassifierTrainer
import ca.uwo.csd.ai.nlp.SVMClassifier
import ca.uwo.csd.ai.nlp.libsvm.svm_model
import ca.uwo.csd.ai.nlp.libsvm.svm_node
import ca.uwo.csd.ai.nlp.libsvm.svm_parameter

class SVMModel[T <: Instance](
  params: JValue,
  outputter: Outputter
) extends BatchModel[T](
  JsonHelper.extractAsOption[Int](params, "max training examples"),
  JsonHelper.extractWithDefault(params, "binarize features", false),
  outputter,
  JsonHelper.extractWithDefault(params, "log level", 3)
) {
  implicit val formats = DefaultFormats
  val allowedParams = Seq("type", "binarize features", "kernel", "log level", "max training examples")
  JsonHelper.ensureNoExtras(params, "operation -> learning", allowedParams)

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
  override def train(featureMatrix: FeatureMatrix, dataset: Dataset[T], featureNames: Seq[String]) = {
    outputter.info("Learning feature weights")
    outputter.info("Prepping training data")

    outputter.info("Creating alphabet")
    val pipes = new mutable.ArrayBuffer[Pipe]
    pipes.+=(new Noop())
    pipes.+=(new Target2Label())
    val pipe = new SerialPipes(pipes.asJava)
    val data = new InstanceList(pipe)
    alphabet = new Alphabet(featureNames.asJava.toArray())

    convertFeatureMatrixToMallet(featureMatrix, dataset, featureNames, data, alphabet)

    outputter.info("Creating the MalletLibSVM object")
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
    svmClassifier.scoreInstance(matrixRowToInstance(row, alphabet))
  }
}
