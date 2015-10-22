package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.features.PathType
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

/**
 * Handles outputting results and other information from running PRA to the file system.  When
 * initialized with node and edge dictionaries, this will output human-readable information.  If
 * node and edge dictionaries are not available, this will fall back on outputting information
 * using the integers used internally by GraphChi (i.e., not very useful).  So we _really_
 * recommend using node and edge dicts with this class.
 *
 * @author mgardner
 *
 */
class Outputter(nodeNames: Map[String, String] = null, fileUtil: FileUtil = new FileUtil) {

  def getNode(index: Int, graph: Graph): String = {
    val node = graph.getNodeName(index)
    if (nodeNames == null) {
      node
    } else {
      nodeNames.getOrElse(node, node)
    }
  }

  def getPathType(pathType: PathType, graph: Graph): String = {
    pathType.encodeAsHumanReadableString(graph)
  }

  def outputScores(filename: String, scores: Seq[(Instance, Double)], config: PraConfig) {
    val trainingInstances = config.trainingData.instances.toSet
    val scoreStrings = scores.map(instanceScore => {
      val instance = instanceScore._1
      val score = instanceScore._2
      val source = getNode(instance.source, instance.graph)
      val target = getNode(instance.target, instance.graph)
      val isPositive = instance.isPositive
      (source, target, isPositive, score, instance)
    })

    val writer = fileUtil.getFileWriter(filename)

    scoreStrings.groupBy(_._1).foreach(sourceScores => {
      val source = sourceScores._1
      val scores = sourceScores._2.sortBy(-_._4)
      for (targetScore <- scores) {
        val target = targetScore._2
        val isPositive = targetScore._3
        val score = targetScore._4
        val instance = targetScore._5
        writer.write(source + "\t" + target + "\t" + score + "\t")
        if (isPositive) {
          writer.write("*")
        }
        if (trainingInstances.contains(instance)) {
          writer.write("^")
        }
        writer.write("\n")
      }
      writer.write("\n")
    })
    writer.close()
  }

  def outputWeights(filename: String, weights: Seq[Double], featureNames: Seq[String]) {
    val lines = weights.zip(featureNames).sortBy(-_._1).map(weight => {
      s"${weight._2}\t${weight._1}"
    })
    fileUtil.writeLinesToFile(filename, lines.asJava)
  }

  def outputSplitFiles(outputBase: String, trainingData: Dataset, testingData: Dataset) {
    if (outputBase != null) {
      fileUtil.writeLinesToFile(outputBase + "training_data.tsv", trainingData.instancesToStrings.asJava)
      fileUtil.writeLinesToFile(outputBase + "testing_data.tsv", testingData.instancesToStrings.asJava)
    }
  }

  def outputPathCounts(baseDir: String, filename: String, pathCounts: Map[PathType, Int]) {
    if (baseDir != null) {
      val lines = pathCounts.toList.sortBy(-_._2).map(entry => {
        s"${entry._1}\t${entry._2}"
      })
      fileUtil.writeLinesToFile(baseDir + filename, lines.asJava)
    }
  }

  def outputPathCountMap(
      baseDir: String,
      filename: String,
      pathCountMap: Map[Instance, Map[PathType, Int]],
      data: Dataset) {
    if (baseDir != null) {
      val writer = fileUtil.getFileWriter(baseDir + filename)
      for (instance <- data.instances) {
        writer.write(getNode(instance.source, instance.graph) + "\t"
          + getNode(instance.target, instance.graph) + "\t")
        if (instance.isPositive) {
          writer.write("+\n")
        } else {
          writer.write("-\n")
        }
        val pathCounts = pathCountMap.getOrElse(instance, Map())
        pathCounts.toList.sortBy(-_._2).foreach(entry => {
          val pathTypeStr = getPathType(entry._1, instance.graph)
          writer.write("\t" + pathTypeStr + "\t" + entry._2 + "\n")
        })
        writer.write("\n")
      }
      writer.close()
    }
  }

  def outputPaths(
      baseDir: String,
      filename: String,
      pathTypes: Seq[PathType],
      graph: Graph) {
    if (baseDir != null) {
      fileUtil.writeLinesToFile(baseDir + filename,
        pathTypes.map(p => getPathType(p, graph)).asJava)
    }
  }

  def outputFeatureMatrix(filename: String, matrix: FeatureMatrix, featureNames: Seq[String]) {
    val writer = fileUtil.getFileWriter(filename)
    for (row <- matrix.getRows().asScala) {
      val instance = row.instance
      writer.write(getNode(instance.source, instance.graph) + "," +
        getNode(instance.target, instance.graph) + "\t")
      for (i <- 0 until row.columns) {
        val featureName = featureNames(row.featureTypes(i))
        writer.write(featureName + "," + row.values(i))
        if (i < row.columns - 1) {
           writer.write(" -#- ")
        }
      }
      writer.write("\n")
    }
    writer.close()
  }
}

object Outputter {
  private var logLevel = 3

  def fatal(message: String) { outputAtLevel(message, 0) }
  def error(message: String) { outputAtLevel(message, 1) }
  def warn(message: String) { outputAtLevel(message, 2) }
  def info(message: String) { outputAtLevel(message, 3) }
  def debug(message: String) { outputAtLevel(message, 4) }

  def setLogLevel(level: Int) {
    logLevel = level
  }

  def outputAtLevel(message: String, level: Int) {
    if (level <= logLevel) {
      println(message)
    }
  }
}
