package edu.cmu.ml.rtw.pra.data

import java.io.FileWriter

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.pra.graphs.PprNegativeExampleSelector
import com.mattg.pipeline.Step
import com.mattg.util.Dictionary
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.Pair

import org.json4s._
import org.json4s.native.JsonMethods.{pretty,render}

import com.typesafe.scalalogging.LazyLogging

object SplitCreator {
  def create(params: JValue, praBase: String, fileUtil: FileUtil): SplitCreator = {
    val splitTypes = Seq("fixed split from relation metadata", "add negatives to split")
    val splitType = JsonHelper.extractChoiceWithDefault(params, "type", splitTypes, "fixed split from relation metadata")
    if (splitType == "fixed split from relation metadata") {
      new SplitCreatorFromMetadata(params, praBase, fileUtil)
    } else {
      new SplitCreatorFromExistingSplit(params, praBase, fileUtil)
    }
  }
}

abstract class SplitCreator(
    params: JValue,
    praBase: String,
    fileUtil: FileUtil
) extends Step(Some(params), fileUtil) with LazyLogging {
  implicit val formats = DefaultFormats
  override val name = "Split Creator"

  val baseSplitDir = praBase + "splits/"
  val splitName = (params \ "name").extract[String]
  val splitDir = s"${baseSplitDir}${splitName}/"
  override val inProgressFile = s"${splitDir}in_progress"
  override val paramFile = s"${splitDir}params.json"

  // We'll only set outputs here; subclasses will have to specify their inputs.
  override val outputs = Set(splitDir)

  val relationMetadata = JsonHelper.getPathOrName(params, "relation metadata", praBase, "relation_metadata").get
  val relationMetadataInput: (String, Option[Step]) = (relationMetadata, None)
  val graphDir = JsonHelper.getPathOrName(params, "graph", praBase, "graphs").get
  val graphInput = Graph.getStepInput(params \ "graph", praBase + "graphs/", fileUtil)
  val generateNegativesFor = {
    val choices = Seq("training", "test", "both")
    val choice = JsonHelper.extractChoiceWithDefault(params, "generate negatives for", choices, "both")
    if (choice == "both") {
      Set("training", "testing")
    } else {
      Set(choice)
    }
  }
  val negativeExampleSelector = createNegativeExampleSelector(params \ "negative instances")

  def _runStep() {
    fileUtil.mkdirOrDie(splitDir)
    createSplit()
  }
  def createSplit()

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
        logger.warn(s"Didn't find a category file for type $domain (domain of relation $relation)")
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
        logger.warn(s"Didn't find a category file for type $range (range of relation $relation)")
        None
      }
    }
    negativeExampleSelector.selectNegativeExamples(data, other_positive_instances, allowedSources, allowedTargets)
  }

  def createNegativeExampleSelector(params: JValue): PprNegativeExampleSelector = {
    params match {
      case JNothing => null
      case jval => {
        val graph = new GraphOnDisk(graphDir, fileUtil)
        new PprNegativeExampleSelector(params, graph)
      }
    }
  }
}

class SplitCreatorFromMetadata(
  params: JValue,
  praBase: String,
  fileUtil: FileUtil
) extends SplitCreator(params, praBase, fileUtil) {

  val paramKeys = Seq("name", "type", "relation metadata", "graph", "negative instances",
    "generate negatives for", "relations", "percent training")
  JsonHelper.ensureNoExtras(params, name, paramKeys)

  override val inputs: Set[(String, Option[Step])] = Set(relationMetadataInput) ++ graphInput.toSet

  override def createSplit() {
    logger.info(s"Creating split at $splitDir")
    val percentTraining = (params \ "percent training").extract[Double]
    val relations = (params \ "relations").extract[List[String]]
    if (relations.size == 0) throw new IllegalStateException("You forgot to specify which "
      + "relations to run!")
    fileUtil.touchFile(inProgressFile)
    val params_out = fileUtil.getFileWriter(paramFile)
    params_out.write(pretty(render(params)))
    params_out.close

    val graph = new GraphOnDisk(graphDir, fileUtil)

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
      logger.info("Splitting data")
      val (training, testing) = data.splitData(percentTraining)
      val rel_dir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(rel_dir)
      fileUtil.writeLinesToFile(s"${rel_dir}training.tsv", training.instancesToStrings)
      fileUtil.writeLinesToFile(s"${rel_dir}testing.tsv", testing.instancesToStrings)
    }
    fileUtil.deleteFile(inProgressFile)
  }
}

class SplitCreatorFromExistingSplit(
  params: JValue,
  praBase: String,
  fileUtil: FileUtil
) extends SplitCreator(params, praBase, fileUtil) {

  val paramKeys = Seq("name", "type", "relation metadata", "graph", "negative instances",
    "generate negatives for", "from split")
  JsonHelper.ensureNoExtras(params, name, paramKeys)

  val splitInput = Split.getStepInput(params \ "from split", praBase, fileUtil)
  override val inputs: Set[(String, Option[Step])] =
    Set(relationMetadataInput, splitInput) ++ graphInput.toSet

  override def createSplit() {
    logger.info(s"Creating split at $splitDir")

    val graph = new GraphOnDisk(graphDir, fileUtil)

    val range_file = s"${relationMetadata}ranges.tsv"
    val ranges = if (fileUtil.fileExists(range_file)) fileUtil.readMapFromTsvFile(range_file) else null
    val domain_file = s"${relationMetadata}domains.tsv"
    val domains = if (fileUtil.fileExists(domain_file)) fileUtil.readMapFromTsvFile(domain_file) else null

    val fromSplitDir = baseSplitDir + (params \ "from split").extract[String] + "/"
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
}
