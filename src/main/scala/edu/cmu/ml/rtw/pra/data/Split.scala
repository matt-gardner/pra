package edu.cmu.ml.rtw.pra.data

import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import org.json4s._

abstract class Split[T <: Instance](params: JValue, baseDir: String, fileUtil: FileUtil) {
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

  def readDatasetFile(filename: String, graph: Option[Graph]): Dataset[T]
}

class NodePairSplit(
  params: JValue,
  baseDir: String,
  fileUtil: FileUtil = new FileUtil
) extends Split[NodePairInstance](params, baseDir, fileUtil) {
  def readDatasetFile(filename: String, graph: Option[Graph]) =
    Dataset.nodePairDatasetFromFile(filename, graph, fileUtil)
}

object Split {
  // So far, we just have a single type of split.  That will change very soon.
  def create(params: JValue, baseDir: String, fileUtil: FileUtil = new FileUtil) = {
    new NodePairSplit(params, baseDir, fileUtil)
  }
}
