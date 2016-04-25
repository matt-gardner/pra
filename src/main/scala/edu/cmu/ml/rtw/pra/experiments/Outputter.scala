package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.features.PathType
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper

import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.native.JsonMethods.{pretty,render,parse}

/**
 * Handles outputting results and other information from running PRA to the file system.
 *
 * @author mgardner
 */
class Outputter(params: JValue, praBase: String, methodName: String, fileUtil: FileUtil = new FileUtil) {
  implicit val formats = DefaultFormats

  // NOTE: using a non-default value for this parameter currently does not interact well with
  // ExperimentRunner.  Specifically, the default check to not re-run experiments will fail.
  val baseDir = JsonHelper.extractWithDefault(params, "outdir", s"${praBase}results/${methodName}/")
  val shouldOutputMatrices = JsonHelper.extractWithDefault(params, "output matrices", false)
  val shouldOutputPaths = JsonHelper.extractWithDefault(params, "output paths", false)
  val shouldOutputPathCounts = JsonHelper.extractWithDefault(params, "output path counts", false)
  val shouldOutputPathCountMap = JsonHelper.extractWithDefault(params, "output path count map", false)

  lazy val nodeNames = (params \ "node names") match {
    case JNothing => null
    case JString(filename) => fileUtil.readMapFromTsvFile(filename, true)
    case _ => throw new IllegalStateException("bad specification of node names")
  }

  private var relation: String = null

  def setRelation(_relation: String) {
    relation = _relation
    fileUtil.mkdirs(baseDir + relation)
  }

  def begin() {
  }

  def clean() {
    fileUtil.deleteFile(baseDir)
  }

  // TODO(matt): it might make sense to redirect outputAtLevel to a proper logging library.  At
  // least this puts all of the logging statements in a single place.
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

  def logToFile(message: String) {
    val writer = fileUtil.getFileWriter(baseDir + "log.txt", true)  // true -> append
    writer.write(message)
    writer.close()
  }

  def writeGlobalParams(_params: JValue) {
    var writer = fileUtil.getFileWriter(baseDir + "params.json")
    writer.write(pretty(render(_params)))
    writer.close()
  }

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

  def outputScores[T <: Instance](
    scores: Seq[(T, Double)],
    trainingData: Dataset[T]
  ) {
    val filename = baseDir + relation + "/scores.tsv"
    trainingData.instances(0) match {
      case nodePairInstance: NodePairInstance =>  {
        outputNodePairScores(
          filename,
          scores.asInstanceOf[Seq[(NodePairInstance, Double)]],
          trainingData.asInstanceOf[Dataset[NodePairInstance]]
        )
      }
      case nodeInstance: NodeInstance => {
        outputNodeScores(
          filename,
          scores.asInstanceOf[Seq[(NodeInstance, Double)]],
          trainingData.asInstanceOf[Dataset[NodeInstance]]
        )
      }
    }
  }

  def outputNodePairScores(
    filename: String,
    scores: Seq[(NodePairInstance, Double)],
    trainingData: Dataset[NodePairInstance]
  ) {
    val trainingInstances = trainingData.instances.toSet
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

  def outputNodeScores(
    filename: String,
    scores: Seq[(NodeInstance, Double)],
    trainingData: Dataset[NodeInstance]
  ) {
    val trainingInstances = trainingData.instances.toSet
    val scoreStrings = scores.map(instanceScore => {
      val instance = instanceScore._1
      val score = instanceScore._2
      val node = getNode(instance.node, instance.graph)
      val isPositive = instance.isPositive
      (node, isPositive, score, instance)
    })

    val writer = fileUtil.getFileWriter(filename)

    scoreStrings.sortBy(-_._3).foreach(scoreString => {
      val node = scoreString._1
      val isPositive = scoreString._2
      val score = scoreString._3
      val instance = scoreString._4
      writer.write(node + "\t" + score + "\t")
      if (isPositive) {
        writer.write("*")
      }
      if (trainingInstances.contains(instance)) {
        writer.write("^")
      }
      writer.write("\n")
    })
    writer.close()
  }

  def outputWeights(weights: Seq[Double], featureNames: Seq[String]) {
    val filename = baseDir + relation + "/weights.tsv"
    val lines = weights.zip(featureNames).sortBy(-_._1).map(weight => {
      s"${weight._2}\t${weight._1}"
    })
    fileUtil.writeLinesToFile(filename, lines)
  }

  def outputDataset[T <: Instance](filename: String, data: Dataset[T]) {
    fileUtil.writeLinesToFile(filename, data.instancesToStrings)
  }

  def outputPathCounts(pathCounts: Map[PathType, Int]) {
    if (shouldOutputPathCounts) {
      val lines = pathCounts.toList.sortBy(-_._2).map(entry => {
        s"${entry._1}\t${entry._2}"
      })
      fileUtil.writeLinesToFile(baseDir + relation + "/path_counts.tsv", lines)
    }
  }

  def outputPathCountMap(
    pathCountMap: Map[NodePairInstance, Map[PathType, Int]],
    data: Dataset[NodePairInstance]
  ) {
    if (shouldOutputPathCountMap) {
      val writer = fileUtil.getFileWriter(baseDir + relation + "/path_count_map.tsv")
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

  def outputPaths(pathTypes: Seq[PathType], graph: Graph) {
    if (shouldOutputPaths) {
      fileUtil.writeLinesToFile(baseDir + relation + "/paths.tsv", pathTypes.map(p => getPathType(p, graph)))
    }
  }

  def outputFeatureMatrix(isTraining: Boolean, matrix: FeatureMatrix, featureNames: Seq[String]) {
    if (shouldOutputMatrices) {
      val trainingStr = if (isTraining) "training_matrix.tsv" else "test_matrix.tsv"
      val filename = baseDir + relation + "/" + trainingStr
      val writer = fileUtil.getFileWriter(filename)
      for (row <- matrix.getRows().asScala) {
        val key = row.instance match {
          case npi: NodePairInstance => {
            getNode(npi.source, npi.graph) + "," + getNode(npi.target, npi.graph)
          }
          case ni: NodeInstance => { getNode(ni.node, ni.graph) }
        }
        val positiveStr = if (row.instance.isPositive) "1" else "-1"
        writer.write(key + "\t" + positiveStr + "\t")
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
}

object Outputter {
  val justLogger = new Outputter(JNothing, "/dev/null", "/dev/null") {
    override def logToFile(message: String) {
      println("NOT LOGGING TO FILE, THIS JUST LOGS TO STDOUT!")
      println("Message was: " + message)
    }
  }
}
