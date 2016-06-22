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

  def outputDataset[T <: Instance](filename: String, data: Dataset[T]) {
    fileUtil.writeLinesToFile(filename, data.instancesToStrings)
  }
}
