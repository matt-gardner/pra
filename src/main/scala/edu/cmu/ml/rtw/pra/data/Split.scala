package edu.cmu.ml.rtw.pra.data

import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphBuilder
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import com.mattg.pipeline.Step
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper

import org.json4s._

sealed abstract class Split[T <: Instance](
  params: JValue,
  baseDir: String,
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
    new GraphInMemory(entries, graphBuilder.nodeDict, graphBuilder.edgeDict)
  }

  def lineToInstance(graph: Graph)(line: String): T
  def lineToInstanceAndGraph(line: String): T
}

class NodePairSplit(
  params: JValue,
  baseDir: String,
  fileUtil: FileUtil = new FileUtil
) extends Split[NodePairInstance](params, baseDir, fileUtil) {
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
  fileUtil: FileUtil = new FileUtil
) extends Split[NodeInstance](params, baseDir, fileUtil) {
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

  /**
   * Creates a Split object with the given params.  Note that this MUST be lightweight because of
   * the way it is used in the pipeline architecture - only do object creation in this method, and
   * in the constructors of all Split objects.  Don't do any processing - make sure that all class
   * members that are expensive to compute are lazy.
   */
  def create(
    params: JValue,
    baseDir: String,
    fileUtil: FileUtil = new FileUtil
  ): Split[_ <: Instance] = {
    val instanceType = JsonHelper.extractWithDefault(params, "node or node pair", "node pair")
    instanceType match {
      case "node pair" => new NodePairSplit(params, baseDir, fileUtil)
      case "node" => new NodeSplit(params, baseDir, fileUtil)
    }
  }


  /**
   * Looks at the parameters and returns the required input split directory, and the Step that will
   * create it, if any.  This piece of code integrates the SplitCreator code with the pipeline
   * architecture in com.mattg.pipeline.
   *
   * The split parameters could just be a string; in that case, we just use it as the split
   * directory and don't try to create anything.  If there are parameters for creating this split,
   * we return a Step object using those parameters, so the pipeline architecture can make sure the
   * split is constructed.
   *
   * The baseDir here is the base experiment directory, _not_ the base split directory.  The split
   * creation code needs to be able to find graphs from the base directory.
   */
  def getStepInput(params: JValue, baseDir: String, fileUtil: FileUtil): (String, Option[Step]) = {
    params match {
      case JString(name) => {
        val splitDir = if (name.startsWith("/")) name else baseDir + "splits/" + name + "/"
        (splitDir, None)
      }
      case jval => {
        jval \ "name" match {
          case JString(name) => {
            val splitDir = baseDir + "splits/" + name + "/"
            val creator: Step = SplitCreator.create(params, baseDir, fileUtil)
            (splitDir, Some(creator))
          }
          case other => throw new IllegalStateException("Malformed (or absent) split specification")
        }
      }
    }
  }
}

object DatasetReader {
  def readNodePairFile(
    filename: String,
    graph: Option[Graph],
    fileUtil: FileUtil = new FileUtil
  ): Dataset[NodePairInstance] = {
    val split = new NodePairSplit(JString("/fake/"), "/", fileUtil)
    split.readDatasetFile(filename, graph)
  }

  def readNodeFile(
    filename: String,
    graph: Option[Graph],
    fileUtil: FileUtil = new FileUtil
  ): Dataset[NodeInstance] = {
    val split = new NodeSplit(JString("/fake/"), "/", fileUtil)
    split.readDatasetFile(filename, graph)
  }
}
