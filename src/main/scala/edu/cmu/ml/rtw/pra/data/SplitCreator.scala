package edu.cmu.ml.rtw.pra.data

import java.io.FileWriter

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.PprNegativeExampleSelector
import com.mattg.util.Dictionary
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.Pair

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
    "negative instances", "from split", "relations", "generate negatives for")
  JsonHelper.ensureNoExtras(params, "split", paramKeys)

  val inProgressFile = SplitCreator.inProgressFile(splitDir)
  val paramFile = SplitCreator.paramFile(splitDir)
  val splitType = JsonHelper.extractWithDefault(params, "type", "fixed split from relation metadata")
  val relationMetadata = JsonHelper.getPathOrName(params, "relation metadata", praBase, "relation_metadata").get
  val graphDir = JsonHelper.getPathOrName(params, "graph", praBase, "graphs").get
  val generateNegativesFor = {
    val choice = JsonHelper.extractChoiceWithDefault(
      params,
      "generate negatives for",
      Seq("training", "test", "both"),
      "both"
    )
    if (choice == "both") {
      Set("training", "testing")
    } else {
      Set(choice)
    }
  }
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
        addNegativeExamples(all_instances, Seq(), relation, domains.toMap, ranges.toMap, graph.nodeDict)
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
      val rel_dir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(rel_dir)

      val training_file = s"${fromSplitDir}${fixed}/training.tsv"
      val training_data = if (fileUtil.fileExists(training_file)) {
        val training_instances = DatasetReader.readNodePairFile(training_file, Some(graph), fileUtil)
        val filtered_training_instances = training_instances.instances
          .filterNot(i => i.source == -1 || i.target == -1)
        new Dataset[NodePairInstance](filtered_training_instances)
      } else {
        new Dataset[NodePairInstance](Seq())
      }

      if (fileUtil.fileExists(training_file)) {
        val negative_training_instances = if (generateNegativesFor.contains("training")) {
          addNegativeExamples(training_data, Seq(), relation, domains.toMap, ranges.toMap, graph.nodeDict)
        } else {
          new Dataset[NodePairInstance](Seq())
        }
        val new_training_file = s"${rel_dir}training.tsv"
        fileUtil.copy(training_file, new_training_file)
        fileUtil.writeLinesToFile(
          new_training_file,
          negative_training_instances.instancesToStrings,
          append=true
        )
      }

      val testing_file = s"${fromSplitDir}${fixed}/testing.tsv"
      if (fileUtil.fileExists(testing_file)) {
        val testing_instances = DatasetReader.readNodePairFile(testing_file, Some(graph), fileUtil)
        val filtered_testing_instances = testing_instances.instances
          .filterNot(i => i.source == -1 || i.target == -1)
        val testing_data = new Dataset[NodePairInstance](filtered_testing_instances)
        val negative_testing_instances = if (generateNegativesFor.contains("testing")) {
          addNegativeExamples(testing_data, training_data.instances, relation, domains.toMap, ranges.toMap, graph.nodeDict)
        } else {
          new Dataset[NodePairInstance](Seq())
        }
        val new_testing_file = s"${rel_dir}testing.tsv"
        fileUtil.copy(testing_file, new_testing_file)
        fileUtil.writeLinesToFile(
          new_testing_file,
          negative_testing_instances.instancesToStrings,
          append=true
        )
      }
    }
    fileUtil.deleteFile(inProgressFile)
  }

  def addNegativeExamples(
      data: Dataset[NodePairInstance],
      other_positive_instances: Seq[NodePairInstance],
      relation: String,
      domains: Map[String, String],
      ranges: Map[String, String],
      node_dict: Dictionary): Dataset[NodePairInstance] = {
    val domain = if (domains == null) null else domains(relation)
    val allowedSources = if (domain == null) None else {
      val fixed = domain.replace("/", "_")
      val domain_file = s"${relationMetadata}category_instances/${fixed}"
      if (fileUtil.fileExists(domain_file)) {
        // The true in this line says that we should drop nodes from the set if they don't show up
        // in the dictionary.
        Some(fileUtil.readIntegerSetFromFile(domain_file, Some(node_dict), true))
      } else {
        outputter.warn(s"Didn't find a category file for type $domain (domain of relation $relation)")
        None
      }
    }
    val range = if (ranges == null) null else ranges(relation)
    val allowedTargets = if (range == null) None else {
      val fixed = range.replace("/", "_")
      val range_file = s"${relationMetadata}category_instances/${fixed}"
      if (fileUtil.fileExists(range_file)) {
        // The true in this line says that we should drop nodes from the set if they don't show up
        // in the dictionary.
        Some(fileUtil.readIntegerSetFromFile(range_file, Some(node_dict), true))
      } else {
        outputter.warn(s"Didn't find a category file for type $range (range of relation $relation)")
        None
      }
    }
    negativeExampleSelector.selectNegativeExamples(data, other_positive_instances, allowedSources, allowedTargets)
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
