package edu.cmu.ml.rtw.pra.experiments

import java.io.FileWriter

import edu.cmu.ml.rtw.pra.config.JsonHelper
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.PprNegativeExampleSelector
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

import org.json4s._
import org.json4s.native.JsonMethods.{pretty,render}

import scala.collection.JavaConverters._

class SplitCreator(
    params: JValue,
    praBase: String,
    splitDir: String,
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
  val percentTraining = (params \ "percent training").extract[Double]
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
    println(s"Creating split at $splitDir")
    val relations = (params \ "relations").extract[List[String]]
    if (relations.size == 0) throw new IllegalStateException("You forgot to specify which "
      + "relations to run!")
    fileUtil.mkdirOrDie(splitDir)
    fileUtil.touchFile(inProgressFile)
    val params_out = fileUtil.getFileWriter(paramFile)
    params_out.write(pretty(render(params)))
    params_out.close

    val graph = new GraphOnDisk(graphDir, fileUtil)

    val range_file = s"${relationMetadata}ranges.tsv"
    val ranges = if (fileUtil.fileExists(range_file)) fileUtil.readMapFromTsvFile(range_file).asScala else null
    val domain_file = s"${relationMetadata}domains.tsv"
    val domains = if (fileUtil.fileExists(domain_file)) fileUtil.readMapFromTsvFile(domain_file).asScala else null

    val relations_to_run = fileUtil.getFileWriter(s"${splitDir}relations_to_run.tsv")
    for (relation <- relations) {
      relations_to_run.write(s"${relation}\n")
    }
    relations_to_run.close()

    for (relation <- relations) {
      val fixed = relation.replace("/", "_")
      val relation_file = s"${relationMetadata}relations/${fixed}"
      val all_instances = Dataset.fromFile(relation_file, Some(graph), fileUtil)
      val data = if (negativeExampleSelector == null) {
        all_instances
      } else {
        addNegativeExamples(all_instances, relation, domains.toMap, ranges.toMap, graph.nodeDict)
      }
      println("Splitting data")
      val (training, testing) = data.splitData(percentTraining)
      val rel_dir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(rel_dir)
      fileUtil.writeLinesToFile(s"${rel_dir}training.tsv", training.instancesToStrings.asJava)
      fileUtil.writeLinesToFile(s"${rel_dir}testing.tsv", testing.instancesToStrings.asJava)
    }
    fileUtil.deleteFile(inProgressFile)
  }

  def addNegativeToSplit() {
    println(s"Creating split at $splitDir")
    fileUtil.mkdirOrDie(splitDir)
    fileUtil.touchFile(inProgressFile)
    val params_out = fileUtil.getFileWriter(paramFile)
    params_out.write(pretty(render(params)))
    params_out.close

    val node_dict = new Dictionary()
    node_dict.setFromReader(fileUtil.getBufferedReader(graphDir + "node_dict.tsv"))
    val edge_dict = new Dictionary()
    edge_dict.setFromReader(fileUtil.getBufferedReader(graphDir + "edge_dict.tsv"))

    val range_file = s"${relationMetadata}ranges.tsv"
    val ranges = if (fileUtil.fileExists(range_file)) fileUtil.readMapFromTsvFile(range_file).asScala else null
    val domain_file = s"${relationMetadata}domains.tsv"
    val domains = if (fileUtil.fileExists(domain_file)) fileUtil.readMapFromTsvFile(domain_file).asScala else null

    val fromSplitDir = praBase + "splits/" + (params \ "from split").extract[String] + "/"
    val relations = fileUtil.readLinesFromFile(fromSplitDir + "relations_to_run.tsv").asScala
    val relations_to_run = fileUtil.getFileWriter(s"${splitDir}relations_to_run.tsv")
    for (relation <- relations) {
      relations_to_run.write(s"${relation}\n")
    }
    relations_to_run.close()

    for (relation <- relations) {
      val fixed = relation.replace("/", "_")
      val training_file = s"${fromSplitDir}${fixed}/training.tsv"
      val testing_file = s"${fromSplitDir}${fixed}/testing.tsv"
      val config = new PraConfig.Builder().setNodeDictionary(node_dict).noChecks().build()
      val training_instances = Dataset.fromFile(training_file, config, fileUtil)
      val new_training_instances =
        addNegativeExamples(training_instances, relation, domains.toMap, ranges.toMap, node_dict)
      val testing_instances = Dataset.fromFile(testing_file, config, fileUtil)
      val new_testing_instances =
        addNegativeExamples(testing_instances, relation, domains.toMap, ranges.toMap, node_dict)
      val rel_dir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(rel_dir)
      fileUtil.writeLinesToFile(s"${rel_dir}training.tsv", new_training_instances.instancesToStrings(node_dict).asJava)
      fileUtil.writeLinesToFile(s"${rel_dir}testing.tsv", new_testing_instances.instancesToStrings(node_dict).asJava)
    }
    fileUtil.deleteFile(inProgressFile)
  }

  def addNegativeExamples(
      data: Dataset,
      relation: String,
      domains: Map[String, String],
      ranges: Map[String, String],
      node_dict: Dictionary): Dataset = {
    val domain = if (domains == null) null else domains(relation)
    val allowedSources = if (domain == null) null else {
      val fixed = domain.replace("/", "_")
      val domain_file = s"${relationMetadata}category_instances/${fixed}"
      fileUtil.readIntegerSetFromFile(domain_file, node_dict).asScala.map(_.toInt).toSet
    }
    val range = if (ranges == null) null else ranges(relation)
    val allowedTargets = if (range == null) null else {
      val fixed = range.replace("/", "_")
      val range_file = s"${relationMetadata}category_instances/${fixed}"
      fileUtil.readIntegerSetFromFile(range_file, node_dict).asScala.map(_.toInt).toSet
    }
    negativeExampleSelector.selectNegativeExamples(data, allowedSources, allowedTargets)
  }

  def createNegativeExampleSelector(params: JValue): PprNegativeExampleSelector = {
    params match {
      case JNothing => null
      case jval => {
        val graphFile = s"${graphDir}graph_chi/edges.tsv"
        val numShards = fileUtil.readIntegerListFromFile(s"${graphDir}num_shards.tsv").get(0)
        new PprNegativeExampleSelector(params, graphFile, numShards)
      }
    }
  }
}

object SplitCreator {
  def inProgressFile(splitDir: String) = s"${splitDir}in_progress"
  def paramFile(splitDir: String) = s"${splitDir}params.json"
}
