package edu.cmu.ml.rtw.pra.experiments

import java.io.FileWriter

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

case class Instance(source: Int, target: Int, isPositive: Boolean)

/**
 * A collection of positive and negative (source, target) pairs.
 */
class Dataset(val instances: Seq[Instance]) {
  def getPosAndNeg() = instances.partition(_.isPositive)
  def getPositiveInstances() = instances.filter(_.isPositive)
  def getNegativeInstances() = instances.filter(!_.isPositive)
  def getAllSources() = instances.map(_.source).toSet
  def getAllTargets() = instances.map(_.target).toSet

  def getSourceMap() = _getSourceMap(instances)
  def getPositiveSourceMap() = _getSourceMap(getPositiveInstances)
  def getNegativeSourceMap() = _getSourceMap(getNegativeInstances)

  def _getSourceMap(instance_list: Seq[Instance]) = instance_list.groupBy(_.source)
    .mapValues(_.map(_.target).toSet)

  /**
   * Takes the examples in this dataset and splits it into two datasets, where the first has
   * percent of the data.  Modulo rounding errors, the ratio of positive to negative examples
   * will be the same in both resultant datasets as it is in the original dataset.
   */
  def splitData(percent: Double): (Dataset, Dataset) = {
    import scala.util.Random
    val random = new Random
    val (positiveInstances, negativeInstances) = getPosAndNeg()

    val numPositiveTraining = (percent * positiveInstances.size).toInt
    random.shuffle(positiveInstances)
    val (trainingPositive, testingPositive) = positiveInstances.splitAt(numPositiveTraining)

    val numNegativeTraining = (percent * negativeInstances.size).toInt
    random.shuffle(negativeInstances)
    val (trainingNegative, testingNegative) = negativeInstances.splitAt(numNegativeTraining)

    val training = new Dataset(trainingPositive ++ trainingNegative)
    val testing = new Dataset(testingPositive ++ testingNegative)
    (training, testing)
  }

  def instancesToStrings(nodeDict: Dictionary): Seq[String] = {
    instances.map(instanceToString(nodeDict))
  }

  def instanceToString(nodeDict: Dictionary)(instance: Instance): String = {
    val pos = if (instance.isPositive) 1 else -1
    if (nodeDict == null) {
      s"${instance.source}\t${instance.target}\t$pos"
    } else {
      s"${nodeDict.getString(instance.source)}\t${nodeDict.getString(instance.target)}\t$pos"
    }
  }

  def merge(other: Dataset) = new Dataset(instances ++ other.instances)
}

object Dataset {
  /**
   * Reads a Dataset from a file.  The format is assumed to be TSV with the following columns:
   *
   * source [tab] target
   *
   * where all examples are assumed to be positive, or
   *
   * source [tab] target [tab] {1,-1}
   *
   * where a 1 in the last column indicates a positive example and a -1 indicates a negative
   * example.
   *
   * The source and target columns can either be strings, with a dictionary provided to convert the
   * strings to integers, or integers that will be parsed.  The logic here doesn't check if the
   * entry is an integer, it just checks if the dictionary is null or not.
   */
  def fromFile(filename: String, dict: Dictionary, fileUtil: FileUtil = new FileUtil): Dataset = {
    val lines = fileUtil.readLinesFromFile(filename).asScala
    val instances = lines.par.map(lineToInstance(dict)).seq
    new Dataset(instances)
  }

  def lineToInstance(dict: Dictionary)(line: String): Instance = {
    val fields = line.split("\t")
    val isPositive = if (fields.size == 2) true else fields(2).toInt == 1
    val source = if (dict == null) fields(0).toInt else dict.getIndex(fields(0))
    val target = if (dict == null) fields(1).toInt else dict.getIndex(fields(1))
    new Instance(source, target, isPositive)
  }
}
