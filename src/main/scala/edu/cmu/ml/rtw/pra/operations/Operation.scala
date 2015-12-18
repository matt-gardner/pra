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
    val unallowedEdges = createUnallowedEdges(relation, inverses, embeddings, builder)
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
      val writer = fileUtil.getFileWriter(builder.outputBase + "log.txt", true)  // true -> append
      writer.write("No range file found! I hope your accept policy is as you want it...\n")
      Outputter.warn("No range file found!")
      writer.close()
    }
  }

  def createUnallowedEdges(
      relation: String,
      inverses: Map[Int, Int],
      embeddings: Map[String, Seq[String]],
      builder: PraConfigBuilder): Seq[Int] = {
    val unallowedEdges = new mutable.ArrayBuffer[Int]

    // TODO(matt): I need a better way to specify this...  It's problematic when there is no shared
    // graph.
    builder.graph match {
      case None => {
        Outputter.warn("\n\n\nNO SHARED GRAPH, SO NO UNALLOWED EDGES!!!\n\n\n")
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
  def create[T <: Instance](
      params: JValue,
      split: Split[T],
      metadataDirectory: String,
      fileUtil: FileUtil): Option[Operation[T]] = {
    val operationType = JsonHelper.extractWithDefault(params, "type", "train and test")
    operationType match {
      case "no op" => None
      case "train and test" => Some(new TrainAndTest(params, split, metadataDirectory, fileUtil))
      case "explore graph" => Some(new ExploreGraph(params, split, fileUtil))
      case "create matrices" => Some(new CreateMatrices(params, split, metadataDirectory, fileUtil))
      case "sgd train and test" => Some(new SgdTrainAndTest(params, split, metadataDirectory, fileUtil))
      case other => throw new IllegalStateException(s"Unrecognized operation: $other")
    }
  }
}

class TrainAndTest[T <: Instance](
  params: JValue,
  split: Split[T],
  metadataDirectory: String,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "features", "learning")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  override def runRelation(configBuilder: PraConfigBuilder) {
    parseRelationMetadata(metadataDirectory, true, configBuilder)

    val config = configBuilder.build()

    // First we get features.
    val generator = FeatureGenerator.create(params \ "features", config, fileUtil)

    // TODO(matt): remove the asInstanceOf
    val trainingData = split.getTrainingData(config.relation, config.graph).asInstanceOf[Dataset[NodePairInstance]]
    val trainingMatrix = generator.createTrainingMatrix(trainingData)

    // TODO(matt): this should be a single call to outputter.outputTrainingMatrix(trainingMatrix).
    // The outputter should have the parameters to decide whether to do anything or not.
    if (config.outputMatrices && config.outputBase != null) {
      val output = config.outputBase + "training_matrix.tsv"
      config.outputter.outputFeatureMatrix(output, trainingMatrix, generator.getFeatureNames())
    }

    // Then we train a model.
    val model = BatchModel.create[NodePairInstance](params \ "learning", config)
    val featureNames = generator.getFeatureNames()
    model.train(trainingMatrix, trainingData, featureNames)

    // Then we test the model.
    // TODO(matt): remove the asInstanceOf
    val testingData = split.getTestingData(config.relation, config.graph).asInstanceOf[Dataset[NodePairInstance]]
    val testMatrix = generator.createTestMatrix(testingData)
    if (config.outputMatrices && config.outputBase != null) {
      val output = config.outputBase + "test_matrix.tsv"
      config.outputter.outputFeatureMatrix(output, testMatrix, generator.getFeatureNames())
    }
    val scores = model.classifyInstances(testMatrix)
    config.outputter.outputScores(config.outputBase + "scores.tsv", scores, trainingData)
  }
}

class ExploreGraph[T <: Instance](
  params: JValue,
  split: Split[T],
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

    val explorer = new GraphExplorer(params \ "explore", config)
    val castData = data.asInstanceOf[Dataset[NodePairInstance]]
    val pathCountMap = explorer.findConnectingPaths(castData)
    config.outputter.outputPathCountMap(config.outputBase, "path_count_map.tsv", pathCountMap, castData)
  }
}

class CreateMatrices[T <: Instance](
  params: JValue,
  split: Split[T],
  metadataDirectory: String,
  fileUtil: FileUtil
) extends Operation[T] {
  override def runRelation(configBuilder: PraConfigBuilder) {
    val config = configBuilder.build()

    // First we get features.
    val generator = FeatureGenerator.create(params \ "features", config, fileUtil)
    val trainingData = split.getTrainingData(config.relation, config.graph)
    // TODO(matt): remove the asInstanceOf
    val trainingMatrix = generator.createTrainingMatrix(trainingData.asInstanceOf[Dataset[NodePairInstance]])
    if (config.outputMatrices && config.outputBase != null) {
      val output = config.outputBase + "training_matrix.tsv"
      config.outputter.outputFeatureMatrix(output, trainingMatrix, generator.getFeatureNames())
    }

  }
}

class SgdTrainAndTest[T <: Instance](
  params: JValue,
  split: Split[T],
  metadataDirectory: String,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "learning", "features", "cache feature vectors")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  val cacheFeatureVectors = JsonHelper.extractWithDefault(params, "cache feature vectors", true)
  val random = new util.Random

  override def runRelation(configBuilder: PraConfigBuilder) {
    parseRelationMetadata(metadataDirectory, true, configBuilder)

    val config = configBuilder.build()

    val featureVectors = new concurrent.TrieMap[Instance, Option[MatrixRow]]

    val model = OnlineModel.create(params \ "learning", config)
    val generator = FeatureGenerator.create(params \ "features", config, fileUtil)

    val trainingData = split.getTrainingData(config.relation, config.graph)

    Outputter.info("Starting learning")
    val start = compat.Platform.currentTime
    for (iteration <- 1 to model.iterations) {
      Outputter.info(s"Iteration $iteration")
      model.nextIteration()
      random.shuffle(trainingData.instances).par.foreach(instance => {
        val matrixRow = if (featureVectors.contains(instance)) {
          featureVectors(instance)
        } else {
          // TODO(matt): remove the asInstanceOf
          val row = generator.constructMatrixRow(instance.asInstanceOf[NodePairInstance])
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
    Outputter.info(s"Learning took $seconds seconds")

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
    val testingData = split.getTestingData(config.relation, config.graph)
    val scores = testingData.instances.par.map(instance => {
      // TODO(matt): remove the asInstanceOf
      val matrixRow = generator.constructMatrixRow(instance.asInstanceOf[NodePairInstance])
      if (cacheFeatureVectors) featureVectors(instance) = matrixRow
      matrixRow match {
        case Some(row) => (instance, model.classifyInstance(row))
        case None => (instance, 0.0)
      }
    }).seq
    config.outputter.outputScores(config.outputBase + "scores.tsv", scores, trainingData)

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
