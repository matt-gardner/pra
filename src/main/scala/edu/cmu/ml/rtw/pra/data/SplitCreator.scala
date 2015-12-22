package edu.cmu.ml.rtw.pra.data

import java.io.FileWriter

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.PprNegativeExampleSelector
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.JsonHelper
import edu.cmu.ml.rtw.users.matt.util.Pair

import org.json4s._
import org.json4s.native.JsonMethods.{pretty,render}

class SplitCreator(
    params: JValue,
    praBase: String,
    splitDir: String,
    outputter: Outputter,
    fileUtil: FileUtil = new FileUtil) {
  implicit val formats = DefaultFormats
  // TODO(matt): now that I've added another type, I should clean this up a bit...  And the graph
  // parameter should go under negative instances.
  val paramKeys = Seq("name", "type", "relation metadata", "graph", "percent training",
    "negative instances", "from split", "relations")
  JsonHelper.ensureNoExtras(params, "split", paramKeys)

  val inProgressFile = SplitCreator.inProgressFile(splitDir)
  val paramFile = SplitCreator.paramFile(splitDir)
  val splitType = JsonHelper.extractWithDefault(params, "type", "fixed split from relation metadata")
  val relationMetadata = JsonHelper.getPathOrName(params, "relation metadata", praBase, "relation_metadata").get
  val graphDir = JsonHelper.getPathOrName(params, "graph", praBase, "graphs").get
  val negativeExampleSelector = createNegativeExampleSelector(params \ "negative instances")

  def createSplit() {
    splitType match {
      case "fixed split from relation metadata" => {
        createSplitFromMetadata()
      }
      case "add negatives to split" => {
        addNegativeToSplit()
      }
      case other => throw new IllegalStateException("Unrecognized split type!")
    }
  }

  def createSplitFromMetadata() {
    outputter.info(s"Creating split at $splitDir")
    val percentTraining = (params \ "percent training").extract[Double]
    val relations = (params \ "relations").extract[List[String]]
    if (relations.size == 0) throw new IllegalStateException("You forgot to specify which "
      + "relations to run!")
    fileUtil.mkdirOrDie(splitDir)
    fileUtil.touchFile(inProgressFile)
    val params_out = fileUtil.getFileWriter(paramFile)
    params_out.write(pretty(render(params)))
    params_out.close

    val graph = new GraphOnDisk(graphDir, outputter, fileUtil)

    val range_file = s"${relationMetadata}ranges.tsv"
    val ranges = if (fileUtil.fileExists(range_file)) fileUtil.readMapFromTsvFile(range_file) else null
    val domain_file = s"${relationMetadata}domains.tsv"
    val domains = if (fileUtil.fileExists(domain_file)) fileUtil.readMapFromTsvFile(domain_file) else null

    val relations_to_run = fileUtil.getFileWriter(s"${splitDir}relations_to_run.tsv")
    for (relation <- relations) {
      relations_to_run.write(s"${relation}\n")
    }
    relations_to_run.close()

    for (relation <- relations) {
      val fixed = relation.replace("/", "_")
      val relation_file = s"${relationMetadata}relations/${fixed}"
      val all_instances = DatasetReader.readNodePairFile(relation_file, Some(graph), fileUtil)
      val data = if (negativeExampleSelector == null) {
        all_instances
      } else {
        addNegativeExamples(all_instances, relation, domains.toMap, ranges.toMap, graph.nodeDict)
      }
      outputter.info("Splitting data")
      val (training, testing) = data.splitData(percentTraining)
      val rel_dir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(rel_dir)
      fileUtil.writeLinesToFile(s"${rel_dir}training.tsv", training.instancesToStrings)
      fileUtil.writeLinesToFile(s"${rel_dir}testing.tsv", testing.instancesToStrings)
    }
    fileUtil.deleteFile(inProgressFile)
  }

  def addNegativeToSplit() {
    outputter.info(s"Creating split at $splitDir")
    fileUtil.mkdirOrDie(splitDir)
    fileUtil.touchFile(inProgressFile)
    val params_out = fileUtil.getFileWriter(paramFile)
    params_out.write(pretty(render(params)))
    params_out.close

    val graph = new GraphOnDisk(graphDir, outputter, fileUtil)

    val range_file = s"${relationMetadata}ranges.tsv"
    val ranges = if (fileUtil.fileExists(range_file)) fileUtil.readMapFromTsvFile(range_file) else null
    val domain_file = s"${relationMetadata}domains.tsv"
    val domains = if (fileUtil.fileExists(domain_file)) fileUtil.readMapFromTsvFile(domain_file) else null

    val fromSplitDir = praBase + "splits/" + (params \ "from split").extract[String] + "/"
    val relations = fileUtil.readLinesFromFile(fromSplitDir + "relations_to_run.tsv")
    val relations_to_run = fileUtil.getFileWriter(s"${splitDir}relations_to_run.tsv")
    for (relation <- relations) {
      relations_to_run.write(s"${relation}\n")
    }
    relations_to_run.close()

    for (relation <- relations) {
      val fixed = relation.replace("/", "_")
      val training_file = s"${fromSplitDir}${fixed}/training.tsv"
      val testing_file = s"${fromSplitDir}${fixed}/testing.tsv"
      val training_instances = DatasetReader.readNodePairFile(training_file, Some(graph), fileUtil)
      val new_training_instances =
        addNegativeExamples(training_instances, relation, domains.toMap, ranges.toMap, graph.nodeDict)
      val testing_instances = DatasetReader.readNodePairFile(testing_file, Some(graph), fileUtil)
      val new_testing_instances =
        addNegativeExamples(testing_instances, relation, domains.toMap, ranges.toMap, graph.nodeDict)
      val rel_dir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(rel_dir)
      fileUtil.writeLinesToFile(s"${rel_dir}training.tsv", new_training_instances.instancesToStrings)
      fileUtil.writeLinesToFile(s"${rel_dir}testing.tsv", new_testing_instances.instancesToStrings)
    }
    fileUtil.deleteFile(inProgressFile)
  }

  def addNegativeExamples(
      data: Dataset[NodePairInstance],
      relation: String,
      domains: Map[String, String],
      ranges: Map[String, String],
      node_dict: Dictionary): Dataset[NodePairInstance] = {
    val domain = if (domains == null) null else domains(relation)
    val allowedSources = if (domain == null) null else {
      val fixed = domain.replace("/", "_")
      val domain_file = s"${relationMetadata}category_instances/${fixed}"
      fileUtil.readIntegerSetFromFile(domain_file, node_dict)
    }
    val range = if (ranges == null) null else ranges(relation)
    val allowedTargets = if (range == null) null else {
      val fixed = range.replace("/", "_")
      val range_file = s"${relationMetadata}category_instances/${fixed}"
      fileUtil.readIntegerSetFromFile(range_file, node_dict)
    }
    negativeExampleSelector.selectNegativeExamples(data, allowedSources, allowedTargets)
  }

  def createNegativeExampleSelector(params: JValue): PprNegativeExampleSelector = {
    params match {
      case JNothing => null
      case jval => {
        val graph = new GraphOnDisk(graphDir, outputter, fileUtil)
        new PprNegativeExampleSelector(params, graph, outputter)
      }
    }
  }
}

object SplitCreator {
  def inProgressFile(splitDir: String) = s"${splitDir}in_progress"
  def paramFile(splitDir: String) = s"${splitDir}params.json"
}
