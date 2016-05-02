package edu.cmu.ml.rtw.pra.data

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphBuilder
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper

import org.json4s._

sealed abstract class Split[T <: Instance](
  params: JValue,
  baseDir: String,
  outputter: Outputter,
  fileUtil: FileUtil
) {
  implicit val formats = DefaultFormats

  val directory = params match {
    case JString(path) if (path.startsWith("/")) => fileUtil.addDirectorySeparatorIfNecessary(path)
    case JString(name) => s"${baseDir}splits/${name}/"
    case jval => s"${baseDir}splits/" + (jval \ "name").extract[String] + "/"
  }

  def relations(): Seq[String] = fileUtil.readLinesFromFile(directory + "relations_to_run.tsv")

  def getTrainingData(relation: String, graph: Option[Graph]) = loadDataset(relation, graph, true)
  def getTestingData(relation: String, graph: Option[Graph]) = loadDataset(relation, graph, false)

  def loadDataset(relation: String, graph: Option[Graph], isTraining: Boolean): Dataset[T] = {
    val fixedRelation = relation.replace("/", "_")
    val dataFile = if (isTraining) "/training.tsv" else "/testing.tsv"
    val filename = directory + fixedRelation + dataFile
    readDatasetFile(filename, graph)
  }

  def readDatasetFile(filename: String, graph: Option[Graph]): Dataset[T] = {
    val lines = fileUtil.readLinesFromFile(filename)
    if (lines(0).split("\t").size == 4) {
      graph match {
        case Some(g) => throw new IllegalStateException(
          "You already specified a graph, but dataset has its own graphs!")
        case None => {
          val instances = lines.par.map(lineToInstanceAndGraph).seq
          new Dataset[T](instances, fileUtil)
        }
      }
    } else {
      val g = graph.get
      val instances = lines.par.map(lineToInstance(g)).seq
      new Dataset[T](instances, fileUtil)
    }
  }

  def readGraphString(graphString: String): GraphInMemory = {
    val graphBuilder = new GraphBuilder(outputter)
    val graphEdges = graphString.split(" ### ")
    for (edge <- graphEdges) {
      val fields = edge.split("\\^,\\^")
      val source = fields(0)
      val relation = fields(1)
      val target = fields(2)
      graphBuilder.addEdge(source, target, relation)
    }
    val entries = graphBuilder.build()
    new GraphInMemory(entries, graphBuilder.nodeDict, graphBuilder.edgeDict)
  }

  def lineToInstance(graph: Graph)(line: String): T
  def lineToInstanceAndGraph(line: String): T
}

class NodePairSplit(
  params: JValue,
  baseDir: String,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
) extends Split[NodePairInstance](params, baseDir, outputter, fileUtil) {
  override def lineToInstance(graph: Graph)(line: String): NodePairInstance = {
    val fields = line.split("\t")
    val source = fields(0)
    val target = fields(1)
    val isPositive =
      try {
        if (fields.size == 2) true else fields(2).toInt == 1
      } catch {
        case e: NumberFormatException =>
          throw new IllegalStateException("Dataset not formatted correctly!")
      }
    val sourceId = if (graph.hasNode(source)) graph.getNodeIndex(source) else -1
    val targetId = if (graph.hasNode(target)) graph.getNodeIndex(target) else -1
    new NodePairInstance(sourceId, targetId, isPositive, graph)
  }

  override def lineToInstanceAndGraph(line: String): NodePairInstance = {
    val fields = line.split("\t")
    val source = fields(0)
    val target = fields(1)
    val isPositive = fields(2).toInt == 1
    val graph = readGraphString(fields(3))
    val sourceId = if (graph.hasNode(source)) graph.getNodeIndex(source) else -1
    val targetId = if (graph.hasNode(target)) graph.getNodeIndex(target) else -1
    new NodePairInstance(sourceId, targetId, isPositive, graph)
  }
}

class NodeSplit(
  params: JValue,
  baseDir: String,
  outputter: Outputter,
  fileUtil: FileUtil = new FileUtil
) extends Split[NodeInstance](params, baseDir, outputter, fileUtil) {
  override def lineToInstance(graph: Graph)(line: String): NodeInstance = {
    val fields = line.split("\t")
    val nodeName = fields(0)
    val isPositive =
      try {
        if (fields.size == 1) true else fields(1).toInt == 1
      } catch {
        case e: NumberFormatException =>
          throw new IllegalStateException("Dataset not formatted correctly!")
      }
    val nodeId = if (graph.hasNode(nodeName)) graph.getNodeIndex(nodeName) else -1
    new NodeInstance(nodeId, isPositive, graph)
  }

  override def lineToInstanceAndGraph(line: String): NodeInstance = {
    val fields = line.split("\t")
    val nodeName = fields(0)
    val isPositive = fields(1).toInt == 1
    val graph = readGraphString(fields(2))
    val nodeId = if (graph.hasNode(nodeName)) graph.getNodeIndex(nodeName) else -1
    new NodeInstance(nodeId, isPositive, graph)
  }
}

object Split {
  def create(
    params: JValue,
    baseDir: String,
    outputter: Outputter,
    fileUtil: FileUtil = new FileUtil
  ): Split[_ <: Instance] = {
    val instanceType = JsonHelper.extractWithDefault(params, "node or node pair", "node pair")
    instanceType match {
      case "node pair" => new NodePairSplit(params, baseDir, outputter, fileUtil)
      case "node" => new NodeSplit(params, baseDir, outputter, fileUtil)
    }
  }
}

object DatasetReader {
  def readNodePairFile(
    filename: String,
    graph: Option[Graph],
    fileUtil: FileUtil = new FileUtil
  ): Dataset[NodePairInstance] = {
    val split = new NodePairSplit(JString("/fake/"), "/", Outputter.justLogger, fileUtil)
    split.readDatasetFile(filename, graph)
  }

  def readNodeFile(
    filename: String,
    graph: Option[Graph],
    fileUtil: FileUtil = new FileUtil
  ): Dataset[NodeInstance] = {
    val split = new NodeSplit(JString("/fake/"), "/", Outputter.justLogger, fileUtil)
    split.readDatasetFile(filename, graph)
  }
}

