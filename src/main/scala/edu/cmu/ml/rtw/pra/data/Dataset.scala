package edu.cmu.ml.rtw.pra.data

import java.io.FileWriter

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.pra.graphs.GraphBuilder
import edu.cmu.ml.rtw.users.matt.util.FileUtil

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
  def nodePairDatasetFromFile(
    filename: String,
    graph: Option[Graph],
    fileUtil: FileUtil = new FileUtil
  ): Dataset[NodePairInstance] = {
    val lines = fileUtil.readLinesFromFile(filename)
    if (lines(0).split("\t").size == 4) {
      graph match {
        case Some(g) => throw new IllegalStateException(
          "You already specified a graph, but dataset has its own graphs!")
        case None => {
          val instances = lines.par.map(lineToNodePairInstanceAndGraph).seq
          new Dataset[NodePairInstance](instances, fileUtil)
        }
      }
    } else {
      val instances = lines.par.map(lineToNodePairInstance(graph.get)).seq
      new Dataset[NodePairInstance](instances, fileUtil)
    }
  }

  def lineToNodePairInstance(graph: Graph)(line: String): NodePairInstance = {
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
    new NodePairInstance(source, target, isPositive, graph)
  }

  def lineToNodePairInstanceAndGraph(line: String): NodePairInstance = {
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
    new NodePairInstance(sourceId, targetId, isPositive, graph)
  }
}
