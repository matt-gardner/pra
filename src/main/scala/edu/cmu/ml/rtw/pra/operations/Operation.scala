package edu.cmu.ml.rtw.pra.operations

import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.Split
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
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

trait Operation[T <: Instance] {
  def runRelation(configBuilder: PraConfigBuilder)

  // TODO(matt): These methods should be moved to a RelationMetadata object!

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
      outputter: Outputter,
      fileUtil: FileUtil = new FileUtil) {
    val inverses = createInverses(directory, builder, fileUtil)
    val relation = builder.relation
    builder.setRelationInverses(inverses)

    val embeddings = {
      if (directory != null && fileUtil.fileExists(directory + "embeddings.tsv")) {
        fileUtil.readMapListFromTsvFile(directory + "embeddings.tsv")
      } else {
        null
      }
    }
    val unallowedEdges = createUnallowedEdges(relation, inverses, embeddings, outputter, builder)
    builder.setUnallowedEdges(unallowedEdges)

    if (directory != null && useRange && fileUtil.fileExists(directory + "ranges.tsv")) {
      val ranges = fileUtil.readMapFromTsvFile(directory + "ranges.tsv")
      val range = ranges.get(relation) match {
        case None => throw new IllegalStateException(
            "You specified a range file, but it doesn't contain an entry for relation " + relation)
        case Some(r) => r
      }
      val fixed = range.replace("/", "_")
      val cat_file = directory + "category_instances/" + fixed

      val allowedTargets = {
        val lines = fileUtil.readLinesFromFile(cat_file)
        val graph = builder.graph.get
        lines.map(line => graph.getNodeIndex(line)).toSet
      }
      builder.setAllowedTargets(allowedTargets)
    } else {
      outputter.logToFile("No range file found! I hope your accept policy is as you want it...\n")
      outputter.warn("No range file found!")
    }
  }

  def createUnallowedEdges(
      relation: String,
      inverses: Map[Int, Int],
      embeddings: Map[String, Seq[String]],
      outputter: Outputter,
      builder: PraConfigBuilder): Seq[Int] = {
    val unallowedEdges = new mutable.ArrayBuffer[Int]

    // TODO(matt): I need a better way to specify this...  It's problematic when there is no shared
    // graph.
    builder.graph match {
      case None => {
        outputter.warn("\n\n\nNO SHARED GRAPH, SO NO UNALLOWED EDGES!!!\n\n\n")
        return unallowedEdges.toSeq
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
    unallowedEdges.toSeq
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
        for (line <- fileUtil.readLinesFromFile(filename)) {
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
  // I had this returning an Option[Operation[T]], and I liked that, because it removes the need
  // for NoOp.  However, it gave me a compiler warning about inferred existential types, and
  // leaving it without the Option doesn't give me that warning.  So, I'll take the slightly more
  // ugly design instead of the compiler warning.
  def create[T <: Instance](
      params: JValue,
      split: Split[T],
      metadataDirectory: String,
      outputter: Outputter,
      fileUtil: FileUtil): Operation[T] = {
    val operationType = JsonHelper.extractWithDefault(params, "type", "train and test")
    operationType match {
      case "no op" => new NoOp[T]
      case "train and test" => new TrainAndTest(params, split, metadataDirectory, outputter, fileUtil)
      case "explore graph" => new ExploreGraph(params, split, outputter, fileUtil)
      case "create matrices" => new CreateMatrices(params, split, metadataDirectory, outputter, fileUtil)
      case "sgd train and test" => new SgdTrainAndTest(params, split, metadataDirectory, outputter, fileUtil)
      case other => throw new IllegalStateException(s"Unrecognized operation: $other")
    }
  }
}

class NoOp[T <: Instance] extends Operation[T] {
  override def runRelation(configBuilder: PraConfigBuilder) { }
}

class TrainAndTest[T <: Instance](
  params: JValue,
  split: Split[T],
  metadataDirectory: String,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "features", "learning")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  override def runRelation(configBuilder: PraConfigBuilder) {
    parseRelationMetadata(metadataDirectory, true, configBuilder, outputter)

    val config = configBuilder.build()

    // First we get features.
    val generator = FeatureGenerator.create(params \ "features", config, split, outputter, fileUtil)

    val trainingData = split.getTrainingData(config.relation, config.graph)
    val trainingMatrix = generator.createTrainingMatrix(trainingData)
    outputter.outputFeatureMatrix(true, trainingMatrix, generator.getFeatureNames())

    // Then we train a model.
    val model = BatchModel.create(params \ "learning", split, outputter)
    val featureNames = generator.getFeatureNames()
    model.train(trainingMatrix, trainingData, featureNames)

    // Then we test the model.
    val testingData = split.getTestingData(config.relation, config.graph)
    val testMatrix = generator.createTestMatrix(testingData)
    outputter.outputFeatureMatrix(false, testMatrix, generator.getFeatureNames())
    val scores = model.classifyInstances(testMatrix)
    outputter.outputScores(scores, trainingData)
  }
}

class ExploreGraph[T <: Instance](
  params: JValue,
  split: Split[T],
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "explore", "data")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  override def runRelation(configBuilder: PraConfigBuilder) {
    val config = configBuilder.setNoChecks().build()

    val dataToUse = JsonHelper.extractWithDefault(params, "data", "both")
    val data = if (dataToUse == "both") {
      val trainingData = split.getTrainingData(config.relation, config.graph)
      val testingData = split.getTestingData(config.relation, config.graph)
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
      dataToUse match {
        case "training" => split.getTrainingData(config.relation, config.graph)
        case "testing" => split.getTestingData(config.relation, config.graph)
        case _ => throw new IllegalStateException(s"unrecognized data specification: $dataToUse")
      }
    }

    val explorer = new GraphExplorer(params \ "explore", config, outputter)
    val castData = data.asInstanceOf[Dataset[NodePairInstance]]
    val pathCountMap = explorer.findConnectingPaths(castData)
    outputter.outputPathCountMap(pathCountMap, castData)
  }
}

class CreateMatrices[T <: Instance](
  params: JValue,
  split: Split[T],
  metadataDirectory: String,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "features", "data")
  val dataOptions = Seq("both", "training", "testing")
  val dataToUse = JsonHelper.extractOptionWithDefault(params, "data", dataOptions, "both")

  override def runRelation(configBuilder: PraConfigBuilder) {
    parseRelationMetadata(metadataDirectory, true, configBuilder, outputter)
    val config = configBuilder.build()

    val generator = FeatureGenerator.create(params \ "features", config, split, outputter, fileUtil)

    if (dataToUse == "training" || dataToUse == "both") {
      val trainingData = split.getTrainingData(config.relation, config.graph)
      val trainingMatrix = generator.createTrainingMatrix(trainingData)
      outputter.outputFeatureMatrix(true, trainingMatrix, generator.getFeatureNames())
    }
    if (dataToUse == "testing" || dataToUse == "both") {
      val testingData = split.getTestingData(config.relation, config.graph)
      val testingMatrix = generator.createTestMatrix(testingData)
      outputter.outputFeatureMatrix(false, testingMatrix, generator.getFeatureNames())
    }
  }
}

class SgdTrainAndTest[T <: Instance](
  params: JValue,
  split: Split[T],
  metadataDirectory: String,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "learning", "features", "cache feature vectors")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  val cacheFeatureVectors = JsonHelper.extractWithDefault(params, "cache feature vectors", true)
  val random = new util.Random

  override def runRelation(configBuilder: PraConfigBuilder) {
    parseRelationMetadata(metadataDirectory, true, configBuilder, outputter)

    val config = configBuilder.build()

    val featureVectors = new concurrent.TrieMap[Instance, Option[MatrixRow]]

    val model = OnlineModel.create(params \ "learning", outputter)
    val generator = FeatureGenerator.create(params \ "features", config, split, outputter, fileUtil)

    val trainingData = split.getTrainingData(config.relation, config.graph)

    outputter.info("Starting learning")
    val start = compat.Platform.currentTime
    for (iteration <- 1 to model.iterations) {
      outputter.info(s"Iteration $iteration")
      model.nextIteration()
      random.shuffle(trainingData.instances).par.foreach(instance => {
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
    outputter.info(s"Learning took $seconds seconds")

    val featureNames = generator.getFeatureNames()
    outputter.outputWeights(model.getWeights(), featureNames)
    // TODO(matt): I should probably add something to the outputter to append to the matrix file,
    // or something, so I can have this call above and not have to keep around the feature vectors,
    // especially if I'm not caching them.  Same for the test matrix below.  I could also possibly
    // make the matrix a lazy parameter, hidden within a function call, so that I don't have to
    // compute it here if it's not going to be needed...
    val trainingMatrix = new FeatureMatrix(featureVectors.values.flatMap(_ match {
      case Some(row) => Seq(row)
      case _ => Seq()
    }).toList.asJava)
    outputter.outputFeatureMatrix(true, trainingMatrix, generator.getFeatureNames())

    featureVectors.clear

    // Now we test the model.
    val testingData = split.getTestingData(config.relation, config.graph)
    val scores = testingData.instances.par.map(instance => {
      val matrixRow = generator.constructMatrixRow(instance)
      if (cacheFeatureVectors) featureVectors(instance) = matrixRow
      matrixRow match {
        case Some(row) => (instance, model.classifyInstance(row))
        case None => (instance, 0.0)
      }
    }).seq
    outputter.outputScores(scores, trainingData)

    val testingMatrix = new FeatureMatrix(featureVectors.values.flatMap(_ match {
      case Some(row) => Seq(row)
      case _ => Seq()
    }).toList.asJava)
    outputter.outputFeatureMatrix(false, testingMatrix, generator.getFeatureNames())
  }
}
