package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.Split
import edu.cmu.ml.rtw.pra.data.NodePairSplit
import edu.cmu.ml.rtw.pra.data.NodeSplit
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import scala.collection.JavaConverters._

import org.json4s._

trait FeatureGenerator[T <: Instance] {

  /**
   * Constructs a MatrixRow for a single instance.  This is intended for SGD-style training or
   * online prediction.  Note that this could be _really_ inefficient for some kinds of feature
   * generators, and so far is only implemented for SFE.
   */
  def constructMatrixRow(instance: T): Option[MatrixRow]

  /**
   * Takes the data, probably does some random walks (or maybe some matrix multiplications, or a
   * few other possibilities), and returns a FeatureMatrix.
   */
  def createTrainingMatrix(data: Dataset[T]): FeatureMatrix

  /**
   * For efficiency in creating the test matrix, we might drop some features if they have zero
   * weight.  In some FeatureGenerator implementations, computing feature values can be very
   * expensive, so this allows us to save some work.  The return value is the updated set of
   * weights, with any desired values removed.  Yes, this potentially changes the indices and thus
   * the meaning of the feature matrix.  Thus the updated weights can't be used anymore on the
   * training matrix, only on the test matrix.
   */
  def removeZeroWeightFeatures(weights: Seq[Double]): Seq[Double]

  /**
   * Constructs a matrix for the test data.  In general, if this step is dependent on training
   * (because, for instance, a feature set was selected at training time), the FeatureGenerator
   * should save that state internally, and use it to do this computation.  Not all implementations
   * need internal state to do this, but some do.
   */
  def createTestMatrix(data: Dataset[T]): FeatureMatrix

  /**
   * Returns a string representation of the features in the feature matrix.  This need only be
   * defined after createTrainingMatrix is called once, and calling removeZeroWeightFeatures may
   * change the output of this function (because the training and test matrices may have different
   * feature spaces; see comments above).
   */
  def getFeatureNames(): Array[String]

  def createEdgesToExclude(
    instances: Seq[T],
    unallowedEdges: Seq[Int]
  ): Seq[((Int, Int), Int)] = {
    // If there was no input data (e.g., if we are actually trying to predict new edges, not
    // just hide edges from ourselves to try to recover), then there aren't any edges to
    // exclude.  So return an empty list.
    if (unallowedEdges == null) {
      return Seq()
    }
    instances.flatMap(_ match {
      case instance: NodePairInstance => {
        unallowedEdges.map(edge => {
          ((instance.source, instance.target), edge.toInt)
        })
      }
      case _ => throw new NotImplementedError
    })
  }
}

object FeatureGenerator {
  // We need to take the split as input here because it's what contains the type information.  I
  // need a concrete object to match type on.
  def create[T <: Instance](
      params: JValue,
      config: PraConfig,
      split: Split[T],
      fileUtil: FileUtil = new FileUtil): FeatureGenerator[T] = {
    val featureType = JsonHelper.extractWithDefault(params, "type", "subgraphs")
    Outputter.info("feature type being used is " + featureType)
    featureType match {
      case "pra" => {
        split match {
          case s: NodePairSplit => { new PraFeatureGenerator(params, config, fileUtil) }
          case s: NodeSplit => { throw new IllegalStateException("Can't use PRA features with just nodes") }
          case _ => throw new IllegalStateException("How did you get a split that doesn't match?")
        }
      }
      case "subgraphs" => {
        split match {
          case s: NodePairSplit => { new NodePairSubgraphFeatureGenerator(params, config, fileUtil) }
          case s: NodeSplit => { throw new NotImplementedError }
          case _ => throw new IllegalStateException("How did you get a split that doesn't match?")
        }
      }
      case other => throw new IllegalStateException("Illegal feature type!")
    }
  }
}
