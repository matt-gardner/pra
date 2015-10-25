package edu.cmu.ml.rtw.pra.experiments

import java.io.FileWriter

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.pra.graphs.GraphBuilder
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

case class Instance(source: Int, target: Int, isPositive: Boolean, graph: Graph)

/**
 * A collection of positive and negative (source, target) pairs.
 */
class Dataset(
    val instances: Seq[Instance],
    val fileUtil: FileUtil = new FileUtil) {
  def getPosAndNeg() = instances.partition(_.isPositive)
  def getPositiveInstances() = instances.filter(_.isPositive)
  def getNegativeInstances() = instances.filter(!_.isPositive)

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

  def instancesToStrings(): Seq[String] = {
    instances.map(instanceToString)
  }

  def instanceToString(instance: Instance): String = {
    val pos = if (instance.isPositive) 1 else -1
    val graph = instance.graph
    s"${graph.getNodeName(instance.source)}\t${graph.getNodeName(instance.target)}\t$pos"
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
  def fromFile(filename: String, graph: Option[Graph], fileUtil: FileUtil = new FileUtil): Dataset = {
    val lines = fileUtil.readLinesFromFile(filename)
    if (lines(0).split("\t").size == 4) {
      graph match {
        case Some(g) => throw new IllegalStateException(
          "You already specified a graph, but dataset has its own graphs!")
        case None => {
          val instances = lines.par.map(lineToInstanceAndGraph).seq
          new Dataset(instances, fileUtil)
        }
      }
    } else {
      val instances = lines.par.map(lineToInstance(graph.get)).seq
      new Dataset(instances, fileUtil)
    }
  }

  def lineToInstance(graph: Graph)(line: String): Instance = {
    val fields = line.split("\t")
    val isPositive =
      try {
        if (fields.size == 2) true else fields(2).toInt == 1
      } catch {
        case e: NumberFormatException =>
          throw new IllegalStateException("Dataset not formatted correctly!")
      }
    val source = graph.getNodeIndex(fields(0))
    val target = graph.getNodeIndex(fields(1))
    new Instance(source, target, isPositive, graph)
  }

  def lineToInstanceAndGraph(line: String): Instance = {
    val instanceFields = line.split("\t")
    val instanceSource = instanceFields(0)
    val instanceTarget = instanceFields(1)
    val isPositive = instanceFields(2).toInt == 1
    val graphString = instanceFields(3)
    val graphBuilder = new GraphBuilder()
    val graphEdges = graphString.split(" ### ")
    for (edge <- graphEdges) {
      val fields = edge.split("\\^,\\^")
      val source = fields(0)
      val relation = fields(1)
      val target = fields(2)
      graphBuilder.addEdge(source, target, relation)
    }
    val entries = graphBuilder.build()
    val graph = new GraphInMemory(entries, graphBuilder.nodeDict, graphBuilder.edgeDict)
    val sourceId = graph.getNodeIndex(instanceSource)
    val targetId = graph.getNodeIndex(instanceTarget)
    new Instance(sourceId, targetId, isPositive, graph)
  }
}
