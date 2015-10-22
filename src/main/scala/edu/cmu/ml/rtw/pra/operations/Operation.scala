package edu.cmu.ml.rtw.pra.operations

import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.features.FeatureGenerator
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.graphs.GraphExplorer
import edu.cmu.ml.rtw.pra.models.BatchModel
import edu.cmu.ml.rtw.pra.models.OnlineModel
import edu.cmu.ml.rtw.users.matt.util.FileUtil
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.collection.mutable

import org.json4s._

trait Operation {
  def runRelation(configBuilder: PraConfigBuilder)

  // TODO(matt): None of these methods should be here!  See note under TrainAndTest.runRelation
  // below.
  def initializeSplit(
      splitsDirectory: String,
      relationMetadataDirectory: String,
      builder: PraConfigBuilder,
      fileUtil: FileUtil = new FileUtil) = {
    // The Dataset objects need access to the graph information, which is contained in PraConfig.
    // That's all that Dataset needs from PraConfig, so we can safely call builder.build() here and
    // keep the PraConfig object.  Not the best solution, but it will work for now.
    val config = builder.setNoChecks().build()
    val relation = config.relation
    val fixed = relation.replace("/", "_")
    // We look in the splits directory for a fixed split if we don't find one, we do cross
    // validation.
    if (fileUtil.fileExists(splitsDirectory + fixed)) {
      val training = splitsDirectory + fixed + "/training.tsv"
      val testing = splitsDirectory + fixed + "/testing.tsv"
      if (fileUtil.fileExists(training)) {
        builder.setTrainingData(Dataset.fromFile(training, config.graph, fileUtil))
      } else {
        println("WARNING: NO TRAINING FILE FOUND")
      }
      if (fileUtil.fileExists(testing)) {
        builder.setTestingData(Dataset.fromFile(testing, config.graph, fileUtil))
      } else {
        println("WARNING: NO TESTING FILE FOUND")
      }
      false
    } else {
      if (relationMetadataDirectory == null) {
        throw new IllegalStateException("Must specify a relation metadata directory if you do not "
          + "have a fixed split!")
      }
      builder.setAllData(
        Dataset.fromFile(relationMetadataDirectory + "relations/" + fixed, config.graph, fileUtil))
      val percent_training_file = splitsDirectory + "percent_training.tsv"
      builder.setPercentTraining(fileUtil.readDoubleListFromFile(percent_training_file).get(0))
      true
    }
  }

  /**
   * Here we set up the PraConfig items that have to do with the input KB files.  In particular,
   * that means deciding which relations are known to be inverses of each other, which edges
   * should be ignored because using them to predict new relations instances would consitute
   * cheating, and setting the range and domain of a relation to restrict new predictions.
   *
   * Also, if the relations have been embedded into a latent space, we perform a mapping here
   * when deciding which edges to ignore.  This means that each embedding of a KB graph has to
   * have a different directory.
   */
  def parseRelationMetadata(
      directory: String,
      useRange: Boolean,
      builder: PraConfigBuilder,
      fileUtil: FileUtil = new FileUtil) {
    val inverses = createInverses(directory, builder, fileUtil)
    val relation = builder.relation
    builder.setRelationInverses(inverses)

    val embeddings = {
      if (directory != null && fileUtil.fileExists(directory + "embeddings.tsv")) {
        fileUtil.readMapListFromTsvFile(directory + "embeddings.tsv").asScala
          .mapValues(_.asScala.toList).toMap
      } else {
        null
      }
    }
    val unallowedEdges = createUnallowedEdges(relation, inverses, embeddings, builder)
    builder.setUnallowedEdges(unallowedEdges)

    if (directory != null && useRange && fileUtil.fileExists(directory + "ranges.tsv")) {
      val ranges = fileUtil.readMapFromTsvFile(directory + "ranges.tsv")
      val range = ranges.get(relation)
      if (range == null) {
        throw new IllegalStateException(
            "You specified a range file, but it doesn't contain an entry for relation " + relation)
      }
      val fixed = range.replace("/", "_")
      val cat_file = directory + "category_instances/" + fixed

      val allowedTargets = {
        val lines = fileUtil.readLinesFromFile(cat_file).asScala
        val graph = builder.graph.get
        lines.map(line => graph.getNodeIndex(line)).toSet
      }
      builder.setAllowedTargets(allowedTargets)
    } else {
      val writer = fileUtil.getFileWriter(builder.outputBase + "log.txt", true)  // true -> append
      writer.write("No range file found! I hope your accept policy is as you want it...\n")
      println("No range file found!")
      writer.close()
    }
  }

  def createUnallowedEdges(
      relation: String,
      inverses: Map[Int, Int],
      embeddings: Map[String, List[String]],
      builder: PraConfigBuilder): List[Int] = {
    val unallowedEdges = new mutable.ArrayBuffer[Int]

    // TODO(matt): I need a better way to specify this...  It's problematic when there is no shared
    // graph.
    builder.graph match {
      case None => {
        println("\n\n\nNO SHARED GRAPH, SO NO UNALLOWED EDGES!!!\n\n\n")
        return unallowedEdges.toList
      }
      case _ => { }
    }
    val graph = builder.graph.get

    // The relation itself is an unallowed edge type.
    val relIndex = graph.getEdgeIndex(relation)
    unallowedEdges += relIndex

    val inverse = inverses.get(relIndex) match {
      case Some(index) => {
        // If the relation has an inverse, it's an unallowed edge type.
        unallowedEdges += index
        graph.getEdgeName(index)
      }
      case _ => null
    }

    // And if the relation has an embedding (really a set of cluster ids), those should be
    // added to the unallowed edge type list.
    if (embeddings != null) {
      for (embedded <- embeddings.getOrElse(relation, Nil)) {
        unallowedEdges += graph.getEdgeIndex(embedded)
      }
      if (inverse != null) {
        for (embedded <- embeddings.getOrElse(inverse, Nil)) {
          unallowedEdges += graph.getEdgeIndex(embedded)
        }
      }
    }
    unallowedEdges.toList
  }

  /**
   * Reads a file containing a mapping between relations and their inverses, and returns the
   * result as a map.
   */
  def createInverses(
      directory: String,
      builder: PraConfigBuilder,
      fileUtil: FileUtil = new FileUtil): Map[Int, Int] = {
    val inverses = new mutable.HashMap[Int, Int]
    if (directory == null) {
      inverses.toMap
    } else {
      val graph = builder.graph.get
      val filename = directory + "inverses.tsv"
      if (!fileUtil.fileExists(filename)) {
        inverses.toMap
      } else {
        for (line <- fileUtil.readLinesFromFile(filename).asScala) {
          val parts = line.split("\t")
          val rel1 = graph.getEdgeIndex(parts(0))
          val rel2 = graph.getEdgeIndex(parts(1))
          inverses.put(rel1, rel2)
          // Just for good measure, in case the file only lists each relation once.
          inverses.put(rel2, rel1)
        }
        inverses.toMap
      }
    }
  }

}

object Operation {
  def create(
      params: JValue,
      splitsDirectory: String,
      metadataDirectory: String,
      fileUtil: FileUtil): Operation = {
    val operationType = JsonHelper.extractWithDefault(params, "type", "train and test")
    operationType match {
      case "no op" => new NoOp()
      case "train and test" => new TrainAndTest(params, splitsDirectory, metadataDirectory, fileUtil)
      case "explore graph" => new ExploreGraph(params, splitsDirectory, fileUtil)
      case "create matrices" => new CreateMatrices(params, splitsDirectory, metadataDirectory, fileUtil)
      case "sgd train and test" => new SgdTrainAndTest(params, splitsDirectory, metadataDirectory, fileUtil)
      case other => throw new IllegalStateException(s"Unrecognized operation: $other")
    }
  }
}

class TrainAndTest(params: JValue, splitsDirectory: String, metadataDirectory: String, fileUtil: FileUtil)
extends Operation {
  val paramKeys = Seq("type", "features", "learning")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  override def runRelation(configBuilder: PraConfigBuilder) {
    parseRelationMetadata(metadataDirectory, true, configBuilder)

    // TODO(matt): these first two statements really should be put into a Split object that gets
    // passed as input to this method (and to CreateMatrices).
    val doCrossValidation = initializeSplit(
      splitsDirectory,
      metadataDirectory,
      configBuilder,
      fileUtil)

    // Split the data if we're doing cross validation instead of a fixed split.
    if (doCrossValidation) {
      val config = configBuilder.build()
      val (trainingData, testingData) = config.allData.splitData(config.percentTraining)
      config.outputter.outputSplitFiles(config.outputBase, trainingData, testingData)
      configBuilder.setAllData(null)
      configBuilder.setPercentTraining(0)
      configBuilder.setTrainingData(trainingData)
      configBuilder.setTestingData(testingData)
    }

    val config = configBuilder.build()

    // First we get features.
    val generator = FeatureGenerator.create(params \ "features", config, fileUtil)
    val trainingMatrix = generator.createTrainingMatrix(config.trainingData)
    // TODO(matt): this should be a single call to outputter.outputTrainingMatrix(trainingMatrix).
    // The outputter should have the parameters to decide whether to do anything or not.
    if (config.outputMatrices && config.outputBase != null) {
      val output = config.outputBase + "training_matrix.tsv"
      config.outputter.outputFeatureMatrix(output, trainingMatrix, generator.getFeatureNames())
    }

    // Then we train a model.
    val model = BatchModel.create(params \ "learning", config)
    val featureNames = generator.getFeatureNames()
    model.train(trainingMatrix, config.trainingData, featureNames)

    // Then we test the model.
    // TODO(matt): if we don't care about removing zero weight features anymore (and it's probably
    // not worth it, anyway), we could feasibly just generate the training and test matrices at the
    // same time, and because of how GraphChi works, that would save us considerable time.
    val testMatrix = generator.createTestMatrix(config.testingData)
    if (config.outputMatrices && config.outputBase != null) {
      val output = config.outputBase + "test_matrix.tsv"
      config.outputter.outputFeatureMatrix(output, testMatrix, generator.getFeatureNames())
    }
    val scores = model.classifyInstances(testMatrix)
    config.outputter.outputScores(config.outputBase + "scores.tsv", scores, config)
  }
}

class ExploreGraph(params: JValue, splitsDirectory: String, fileUtil: FileUtil) extends Operation {
  val paramKeys = Seq("type", "explore", "data")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  override def runRelation(configBuilder: PraConfigBuilder) {
    val config = configBuilder.setNoChecks().build()

    val dataToUse = JsonHelper.extractWithDefault(params, "data", "both")
    val fixed = config.relation.replace("/", "_")
    val data = if (dataToUse == "both") {
      val trainingFile = s"${splitsDirectory}${fixed}/training.tsv"
      val trainingData = if (fileUtil.fileExists(trainingFile))
        Dataset.fromFile(trainingFile, config.graph, fileUtil) else null
      val testingFile = s"${splitsDirectory}${fixed}/testing.tsv"
      val testingData = if (fileUtil.fileExists(testingFile))
        Dataset.fromFile(testingFile, config.graph, fileUtil) else null
      if (trainingData == null && testingData == null) {
        throw new IllegalStateException("Neither training file nor testing file exists for " +
          "relation " + config.relation)
      }
      if (trainingData == null) {
        testingData
      } else if (testingData == null) {
        trainingData
      } else {
        trainingData.merge(testingData)
      }
    } else {
      val inputFile = s"${splitsDirectory}${fixed}/${dataToUse}.tsv"
      Dataset.fromFile(inputFile, config.graph, fileUtil)
    }

    val explorer = new GraphExplorer(params \ "explore", config)
    val pathCountMap = explorer.findConnectingPaths(data)
    config.outputter.outputPathCountMap(config.outputBase, "path_count_map.tsv", pathCountMap, data)
  }
}

class CreateMatrices(params: JValue, splitsDirectory: String, metadataDirectory: String, fileUtil: FileUtil)
extends Operation {
  override def runRelation(configBuilder: PraConfigBuilder) {
    val doCrossValidation = initializeSplit(
      splitsDirectory,
      metadataDirectory,
      configBuilder,
      fileUtil)

    // Split the data if we're doing cross validation instead of a fixed split.
    if (doCrossValidation) {
      val config = configBuilder.build()
      val (trainingData, testingData) = config.allData.splitData(config.percentTraining)
      config.outputter.outputSplitFiles(config.outputBase, trainingData, testingData)
      configBuilder.setAllData(null)
      configBuilder.setPercentTraining(0)
      configBuilder.setTrainingData(trainingData)
      configBuilder.setTestingData(testingData)
    }

    val config = configBuilder.build()

    // First we get features.
    val generator = FeatureGenerator.create(params \ "features", config, fileUtil)
    val trainingMatrix = generator.createTrainingMatrix(config.trainingData)
    if (config.outputMatrices && config.outputBase != null) {
      val output = config.outputBase + "training_matrix.tsv"
      config.outputter.outputFeatureMatrix(output, trainingMatrix, generator.getFeatureNames())
    }

  }
}

class SgdTrainAndTest(params: JValue, splitsDirectory: String, metadataDirectory: String, fileUtil: FileUtil)
extends Operation {
  val paramKeys = Seq("type", "learning", "features", "cache feature vectors")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  val cacheFeatureVectors = JsonHelper.extractWithDefault(params, "cache feature vectors", true)
  val random = new util.Random

  override def runRelation(configBuilder: PraConfigBuilder) {
    parseRelationMetadata(metadataDirectory, true, configBuilder)

    // TODO(matt): these first two statements really should be put into a Split object that gets
    // passed as input to this method (and to CreateMatrices).
    val doCrossValidation = initializeSplit(
      splitsDirectory,
      metadataDirectory,
      configBuilder,
      fileUtil)

    // Split the data if we're doing cross validation instead of a fixed split.
    if (doCrossValidation) {
      val config = configBuilder.build()
      val (trainingData, testingData) = config.allData.splitData(config.percentTraining)
      config.outputter.outputSplitFiles(config.outputBase, trainingData, testingData)
      configBuilder.setAllData(null)
      configBuilder.setPercentTraining(0)
      configBuilder.setTrainingData(trainingData)
      configBuilder.setTestingData(testingData)
    }

    val config = configBuilder.build()

    val featureVectors = new concurrent.TrieMap[Instance, Option[MatrixRow]]

    val model = OnlineModel.create(params \ "learning", config)
    val generator = FeatureGenerator.create(params \ "features", config, fileUtil)

    println("Starting learning")
    val start = compat.Platform.currentTime
    for (iteration <- 1 to model.iterations) {
      println(s"Iteration $iteration")
      model.nextIteration()
      random.shuffle(config.trainingData.instances).par.foreach(instance => {
        val matrixRow = if (featureVectors.contains(instance)) {
          featureVectors(instance)
        } else {
          val row = generator.constructMatrixRow(instance)
          if (cacheFeatureVectors) featureVectors(instance) = row
          row
        }
        matrixRow match {
          case Some(row) => model.updateWeights(row)
          case None => { }
        }
      })
    }
    val end = compat.Platform.currentTime
    val seconds = (end - start) / 1000.0
    println(s"Learning took $seconds seconds")

    val featureNames = generator.getFeatureNames()
    config.outputter.outputWeights(config.outputBase + "weights.tsv", model.getWeights(), featureNames)
    // TODO(matt): this should be a single call to outputter.outputTrainingMatrix(trainingMatrix).
    // The outputter should have the parameters to decide whether to do anything or not.  Also, I
    // should probably add something to the outputter to append to the matrix file, or something,
    // so I can have this call above and not have to keep around the feature vectors, especially if
    // I'm not caching them.  Same for the test matrix below.
    if (config.outputMatrices && config.outputBase != null) {
      val output = config.outputBase + "training_matrix.tsv"
      val trainingMatrix = new FeatureMatrix(featureVectors.values.flatMap(_ match {
        case Some(row) => Seq(row)
        case _ => Seq()
      }).toList.asJava)
      config.outputter.outputFeatureMatrix(output, trainingMatrix, generator.getFeatureNames())
    }

    featureVectors.clear

    // Now we test the model.
    val scores = config.testingData.instances.par.map(instance => {
      val matrixRow = generator.constructMatrixRow(instance)
      if (cacheFeatureVectors) featureVectors(instance) = matrixRow
      matrixRow match {
        case Some(row) => (instance, model.classifyInstance(row))
        case None => (instance, 0.0)
      }
    }).seq
    config.outputter.outputScores(config.outputBase + "scores.tsv", scores, config)

    if (config.outputMatrices && config.outputBase != null) {
      val testingMatrix = new FeatureMatrix(featureVectors.values.flatMap(_ match {
        case Some(row) => Seq(row)
        case _ => Seq()
      }).toList.asJava)
      val output = config.outputBase + "test_matrix.tsv"
      config.outputter.outputFeatureMatrix(output, testingMatrix, generator.getFeatureNames())
    }
  }
}

// TODO(matt): I think Driver should just check for the presence or absence of the "operation"
// parameter, and if there is no operation present it should do a no op.  That would make this
// class unnecessary.
class NoOp extends Operation {
  def runRelation(configBuilder: PraConfigBuilder) { }
}
