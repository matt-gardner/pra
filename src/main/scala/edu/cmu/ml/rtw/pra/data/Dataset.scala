package edu.cmu.ml.rtw.pra.data

import java.io.FileWriter

import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.pra.graphs.GraphBuilder
import com.mattg.util.FileUtil

import scala.collection.JavaConverters._

/**
 * A collection of positive and negative (source, target) pairs.
 */
class Dataset[T <: Instance](val instances: Seq[T], val fileUtil: FileUtil = new FileUtil) {
  def getPosAndNeg() = instances.partition(_.isPositive)
  def getPositiveInstances() = instances.filter(_.isPositive)
  def getNegativeInstances() = instances.filter(!_.isPositive)

  /**
   * Takes the examples in this dataset and splits it into two datasets, where the first has
   * percent of the data.  Modulo rounding errors, the ratio of positive to negative examples
   * will be the same in both resultant datasets as it is in the original dataset.
   */
  def splitData(percent: Double): (Dataset[T], Dataset[T]) = {
    import scala.util.Random
    val random = new Random
    val (positiveInstances, negativeInstances) = getPosAndNeg()

    val numPositiveTraining = (percent * positiveInstances.size).toInt
    random.shuffle(positiveInstances)
    val (trainingPositive, testingPositive) = positiveInstances.splitAt(numPositiveTraining)

    val numNegativeTraining = (percent * negativeInstances.size).toInt
    random.shuffle(negativeInstances)
    val (trainingNegative, testingNegative) = negativeInstances.splitAt(numNegativeTraining)

    val training = new Dataset[T](trainingPositive ++ trainingNegative)
    val testing = new Dataset[T](testingPositive ++ testingNegative)
    (training, testing)
  }

  def instancesToStrings(): Seq[String] = {
    instances.map(_.toString)
  }

  def merge(other: Dataset[T]) = new Dataset[T](instances ++ other.instances)
}
