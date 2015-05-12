package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.features.PathType
import edu.cmu.ml.rtw.users.matt.util.Dictionary
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
class Outputter(
    nodeDict: Dictionary = null,
    edgeDict: Dictionary = null,
    nodeNames: Map[String, String] = null,
    fileUtil: FileUtil = new FileUtil) {

  if (nodeDict == null || edgeDict == null) {
    println("\n\n\n*************************************************\n\n\n")
    println("USING OUTPUTTER WITHOUT NODE AND EDGE DICTIONARIES")
    println("ARE YOU _SURE_ YOU WANT TO DO THIS?")
    println("\n\n\n*************************************************\n\n\n")
  }

  def getNode(index: Int): String = {
    if (nodeDict == null) {
      "" + index
    } else {
      val node = nodeDict.getString(index)
      if (nodeNames == null) {
        node
      } else {
        nodeNames.getOrElse(node, node)
      }
    }
  }

  def getPathType(pathType: PathType): String = {
    if (edgeDict == null) {
      pathType.encodeAsString()
    } else {
      pathType.encodeAsHumanReadableString(edgeDict)
    }
  }

  /**
   * Output a file containing the supplied scores.
   *
   * We take as input all three source maps (found in the config object), in addition to the source
   * scores, because they all have useful information for this.
   *
   * @param filename Place to write the output file
   * @param sourceScores The set of scores for each of the sources; these should already be sorted
   * @param config We use this to get to the training and testing data, so we know which sources
   *     to score and how well we did on them.
   */
  def outputScores(filename: String, sourceScores: Map[Int, Seq[(Int, Double)]], config: PraConfig) = {
    val writer = fileUtil.getFileWriter(filename)
    // These first few lines are for finding out if our prediction was _correct_ or not.
    val trainingSourcesMap = config.trainingData.getPositiveSourceMap()
    val testingSourcesMap = config.testingData.getPositiveSourceMap()
    val positiveTestSources = config.testingData.getPositiveInstances.map(_.source)

    // And this is to know which tuples to _score_ (which might include negative test
    // instances).
    val allTestSources = config.testingData.getSourceMap().keys.toList.sorted
    for (source <- allTestSources) {
      val sourceStr = getNode(source)
      sourceScores.get(source) match {
        case None => {
          writer.write(sourceStr + "\t\t\t")
          if (!positiveTestSources.contains(source)) {
            writer.write("-")
          }
          writer.write("\n\n")
        }
        case Some(scores) => {
          val targetSet = trainingSourcesMap.getOrElse(source, Set()) ++
            testingSourcesMap.getOrElse(source, Set())
          val trainingTargetSet = trainingSourcesMap.getOrElse(source, Set())
          for (targetScore <- scores) {
            val target = targetScore._1
            val score = targetScore._2
            writer.write(sourceStr + "\t" + getNode(target) + "\t" + score + "\t")
            if (targetSet.contains(target)) {
              writer.write("*")
            }
            if (trainingTargetSet.contains(target)) {
              writer.write("^")
            }
            if (!positiveTestSources.contains(source)) {
              writer.write("-")
            }
            writer.write("\n")
          }
          writer.write("\n")
        }
      }
    }
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
      fileUtil.writeLinesToFile(outputBase + "training_data.tsv",
        trainingData.instancesToStrings(nodeDict).asJava)
      fileUtil.writeLinesToFile(outputBase + "testing_data.tsv",
        testingData.instancesToStrings(nodeDict).asJava)
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
      pathCountMap: Map[(Int, Int), Map[PathType, Int]],
      data: Dataset) {
    if (baseDir != null) {
      val writer = fileUtil.getFileWriter(baseDir + filename)
      for (instance <- data.instances) {
        writer.write(getNode(instance.source) + "\t" + getNode(instance.target) + "\t")
        if (instance.isPositive) {
          writer.write("+\n")
        } else {
          writer.write("-\n")
        }
        val pathCounts = pathCountMap.getOrElse((instance.source, instance.target), Map())
        pathCounts.toList.sortBy(-_._2).foreach(entry => {
          writer.write("\t" + getPathType(entry._1) + "\t" + entry._2 + "\n")
        })
        writer.write("\n")
      }
      writer.close()
    }
  }

  def outputPaths(baseDir: String, filename: String, pathTypes: Seq[PathType]) {
    if (baseDir != null) {
      fileUtil.writeLinesToFile(baseDir + filename, pathTypes.map(getPathType).asJava)
    }
  }

  def outputFeatureMatrix(filename: String, matrix: FeatureMatrix, featureNames: Seq[String]) {
    val writer = fileUtil.getFileWriter(filename)
    for (row <- matrix.getRows().asScala) {
      writer.write(getNode(row.sourceNode) + "," + getNode(row.targetNode) + "\t")
      for (i <- 0 until row.columns) {
        val featureName = featureNames(row.pathTypes(i))
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
