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

import scala.util.Random

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
  val baseParamKeys = Seq("name", "type", "relation metadata", "graph", "negative instances",
    "generate negatives for", "max instances")

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
  val maxInstances = JsonHelper.extractAsOption[Int](params, "max instances")
  val random = new Random

  def _runStep() {
    createSplit()
  }
  def createSplit()

  def addNegativeExamples(
      data: Dataset[NodePairInstance],
      otherPositiveInstances: Seq[NodePairInstance],
      relation: String,
      domains: Map[String, String],
      ranges: Map[String, String],
      nodeDict: Dictionary): Dataset[NodePairInstance] = {
    val domain = if (domains == null) null else domains(relation)
    val allowedSources = if (domain == null) None else {
      val fixed = domain.replace("/", "_")
      val domainFile = s"${relationMetadata}category_instances/${fixed}"
      if (fileUtil.fileExists(domainFile)) {
        // The true in this line says that we should drop nodes from the set if they don't show up
        // in the dictionary.
        Some(fileUtil.readIntegerSetFromFile(domainFile, Some(nodeDict), true))
      } else {
        logger.warn(s"Didn't find a category file for type $domain (domain of relation $relation)")
        None
      }
    }
    val range = if (ranges == null) null else ranges(relation)
    val allowedTargets = if (range == null) None else {
      val fixed = range.replace("/", "_")
      val rangeFile = s"${relationMetadata}category_instances/${fixed}"
      if (fileUtil.fileExists(rangeFile)) {
        // The true in this line says that we should drop nodes from the set if they don't show up
        // in the dictionary.
        Some(fileUtil.readIntegerSetFromFile(rangeFile, Some(nodeDict), true))
      } else {
        logger.warn(s"Didn't find a category file for type $range (range of relation $relation)")
        None
      }
    }
    negativeExampleSelector.selectNegativeExamples(data, otherPositiveInstances, allowedSources, allowedTargets)
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

  val paramKeys = baseParamKeys ++ Seq("generate negatives for", "relations", "percent training")
  JsonHelper.ensureNoExtras(params, name, paramKeys)

  override val inputs: Set[(String, Option[Step])] = Set(relationMetadataInput) ++ graphInput.toSet

  override def createSplit() {
    logger.info(s"Creating split at $splitDir")
    val percentTraining = (params \ "percent training").extract[Double]
    val relations = (params \ "relations").extract[List[String]]
    if (relations.size == 0) throw new IllegalStateException("You forgot to specify which "
      + "relations to run!")

    val graph = new GraphOnDisk(graphDir, fileUtil)

    val rangeFile = s"${relationMetadata}ranges.tsv"
    val ranges = if (fileUtil.fileExists(rangeFile)) fileUtil.readMapFromTsvFile(rangeFile) else null
    val domainFile = s"${relationMetadata}domains.tsv"
    val domains = if (fileUtil.fileExists(domainFile)) fileUtil.readMapFromTsvFile(domainFile) else null

    val relationsToRun = fileUtil.getFileWriter(s"${splitDir}relations_to_run.tsv")
    for (relation <- relations) {
      relationsToRun.write(s"${relation}\n")
    }
    relationsToRun.close()

    for (relation <- relations) {
      val fixed = relation.replace("/", "_")
      val relationFile = s"${relationMetadata}relations/${fixed}"
      val allInstances = DatasetReader.readNodePairFile(relationFile, Some(graph), fileUtil)
      val data = if (negativeExampleSelector == null) {
        allInstances
      } else {
        addNegativeExamples(allInstances, Seq(), relation, domains.toMap, ranges.toMap, graph.nodeDict)
      }
      logger.info("Splitting data")
      val (training, testing) = data.splitData(percentTraining)
      val relDir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(relDir)

      val keptTrainingLines = maxInstances match {
        case None => training.instancesToStrings
        case Some(max) => random.shuffle(training.instancesToStrings).take(max)
      }
      val keptTestingLines = maxInstances match {
        case None => testing.instancesToStrings
        case Some(max) => random.shuffle(testing.instancesToStrings).take(max)
      }
      fileUtil.writeLinesToFile(s"${relDir}training.tsv", keptTrainingLines)
      fileUtil.writeLinesToFile(s"${relDir}testing.tsv", keptTestingLines)
    }
    fileUtil.deleteFile(inProgressFile)
  }
}

class SplitCreatorFromExistingSplit(
  params: JValue,
  praBase: String,
  fileUtil: FileUtil
) extends SplitCreator(params, praBase, fileUtil) {

  val paramKeys = baseParamKeys ++ Seq("from split")
  JsonHelper.ensureNoExtras(params, name, paramKeys)

  val splitInput = Split.getStepInput(params \ "from split", praBase, fileUtil)
  val fromSplitDir = baseSplitDir + (params \ "from split").extract[String] + "/"
  val fromSplitInput = (fromSplitDir, None)
  override val inputs: Set[(String, Option[Step])] =
    Set(relationMetadataInput, splitInput, fromSplitInput) ++ graphInput.toSet

  override def createSplit() {
    logger.info(s"Creating split at $splitDir")

    val graph = new GraphOnDisk(graphDir, fileUtil)

    val rangeFile = s"${relationMetadata}ranges.tsv"
    val ranges = if (fileUtil.fileExists(rangeFile)) fileUtil.readMapFromTsvFile(rangeFile) else null
    val domainFile = s"${relationMetadata}domains.tsv"
    val domains = if (fileUtil.fileExists(domainFile)) fileUtil.readMapFromTsvFile(domainFile) else null

    val relations = fileUtil.readLinesFromFile(fromSplitDir + "relations_to_run.tsv")
    val relationsToRun = fileUtil.getFileWriter(s"${splitDir}relations_to_run.tsv")
    for (relation <- relations) {
      relationsToRun.write(s"${relation}\n")
    }
    relationsToRun.close()

    for (relation <- relations) {
      val fixed = relation.replace("/", "_")
      val relDir = s"${splitDir}${fixed}/"
      fileUtil.mkdirs(relDir)

      val trainingFile = s"${fromSplitDir}${fixed}/training.tsv"
      val trainingData = if (fileUtil.fileExists(trainingFile)) {
        val trainingInstances = DatasetReader.readNodePairFile(trainingFile, Some(graph), fileUtil)
        val filteredTrainingInstances = trainingInstances.instances
          .filterNot(i => i.source == -1 || i.target == -1)
        new Dataset[NodePairInstance](filteredTrainingInstances)
      } else {
        new Dataset[NodePairInstance](Seq())
      }

      if (fileUtil.fileExists(trainingFile)) {
        val negativeTrainingInstances = if (generateNegativesFor.contains("training")) {
          addNegativeExamples(trainingData, Seq(), relation, domains.toMap, ranges.toMap, graph.nodeDict)
        } else {
          new Dataset[NodePairInstance](Seq())
        }
        val newTrainingFile = s"${relDir}training.tsv"
        val allLines = fileUtil.readLinesFromFile(trainingFile) ++ negativeTrainingInstances.instancesToStrings
        val keptLines = maxInstances match {
          case None => allLines
          case Some(max) => random.shuffle(allLines).take(max)
        }
        logger.info(s"maxInstances was $maxInstances; kept ${keptLines.size} lines for training")
        fileUtil.writeLinesToFile(newTrainingFile, keptLines)
      }

      val testingFile = s"${fromSplitDir}${fixed}/testing.tsv"
      if (fileUtil.fileExists(testingFile)) {
        val testingInstances = DatasetReader.readNodePairFile(testingFile, Some(graph), fileUtil)
        val filteredTestingInstances = testingInstances.instances
          .filterNot(i => i.source == -1 || i.target == -1)
        val testingData = new Dataset[NodePairInstance](filteredTestingInstances)
        val negativeTestingInstances = if (generateNegativesFor.contains("testing")) {
          addNegativeExamples(testingData, trainingData.instances, relation, domains.toMap, ranges.toMap, graph.nodeDict)
        } else {
          new Dataset[NodePairInstance](Seq())
        }
        val newTestingFile = s"${relDir}testing.tsv"
        val allLines = fileUtil.readLinesFromFile(testingFile) ++ negativeTestingInstances.instancesToStrings
        val keptLines = maxInstances match {
          case None => allLines
          case Some(max) => random.shuffle(allLines).take(max)
        }
        logger.info(s"maxInstances was $maxInstances; kept ${keptLines.size} lines for testing")
        fileUtil.writeLinesToFile(newTestingFile, keptLines)
      }
    }
    fileUtil.deleteFile(inProgressFile)
  }
}
