package edu.cmu.ml.rtw.pra.operations

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.Split
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.data.NodePairSplit
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.features.FeatureGenerator
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.PprComputer
import edu.cmu.ml.rtw.pra.models.BatchModel
import edu.cmu.ml.rtw.pra.models.LogisticRegressionModel
import edu.cmu.ml.rtw.pra.models.OnlineModel

import com.mattg.pipeline.Step
import com.mattg.util.Dictionary
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.MutableConcurrentDictionary

import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.collection.mutable

import org.json4s._
import org.json4s.JsonDSL._

trait Operation[T <: Instance] extends LazyLogging {
  def runRelation(relation: String)
}

object Operation {
  // I had this returning an Option[Operation[T]], and I liked that, because it removes the need
  // for NoOp.  However, it gave me a compiler warning about inferred existential types, and
  // leaving it without the Option doesn't give me that warning.  So, I'll take the slightly more
  // ugly design instead of the compiler warning.
  def create[T <: Instance](
    params: JValue,
    praBase: String,
    graph: Option[Graph],
    split: Split[T],
    relationMetadata: RelationMetadata,
    outputter: Outputter,
    fileUtil: FileUtil
  ): Operation[T] = {
    val operationType = JsonHelper.extractWithDefault(params, "type", "train and test")
    operationType match {
      case "no op" => new NoOp[T]
      case "train and test" =>
        new TrainAndTest(params, graph, split, relationMetadata, outputter, fileUtil)
      case "create matrices" =>
        new CreateMatrices(params, praBase, graph, split, relationMetadata, fileUtil)
      case "sgd train and test" =>
        new SgdTrainAndTest(params, graph, split, relationMetadata, outputter, fileUtil)
      case other => throw new IllegalStateException(s"Unrecognized operation: $other")
    }
  }
}

object Output {
  // I used to have node names available here for Freebase, but I'm not using Freebase like that
  // much anymore, and you can just do the MID -> alias conversion in post processing if you want
  // that.  It adds a ton of complexity to the code to handle that case.
  def getNode(index: Int, graph: Graph): String = {
    graph.getNodeName(index)
  }

  def outputFeatureMatrix(
    filename: String,
    matrix: FeatureMatrix,
    featureNames: Seq[String],
    fileUtil: FileUtil
  ) {
    val writer = fileUtil.getFileWriter(filename)
    for (row <- matrix.getRows().asScala) {
      val key = row.instance match {
        case npi: NodePairInstance => {
          getNode(npi.source, npi.graph) + "," + getNode(npi.target, npi.graph)
        }
        case ni: NodeInstance => { getNode(ni.node, ni.graph) }
      }
      val positiveStr = if (row.instance.isPositive) "1" else "-1"
      writer.write(key + "\t" + positiveStr + "\t")
      for (i <- 0 until row.columns) {
        val featureName = featureNames(row.featureTypes(i))
        writer.write(featureName + "," + row.values(i))
        if (i < row.columns - 1) {
           writer.write(" -#- ")
        }
      }
      writer.write("\n")
    }
    writer.close()
  }
}

class NoOp[T <: Instance] extends Operation[T] {
  override def runRelation(relation: String) { }
}

class BatchTrainer(
  params: JValue,
  baseDir: String,
  experimentDir: String,
  fileUtil: FileUtil
) extends Step(Some(params), fileUtil) with LazyLogging {
  implicit val formats = DefaultFormats
  override val name = "Batch Trainer"
  val validParams = Seq("split", "graph", "relation metadata", "features", "learning", "relation",
    "output feature matrix")
  JsonHelper.ensureNoExtras(params, name, validParams)

  val outputFeatureMatrix = JsonHelper.extractWithDefault(params, "output feature matrix", false)
  val relation = (params \ "relation").extract[String]
  val splitInput = Split.getStepInput(params \ "split", baseDir, fileUtil)
  val graphInput = Graph.getStepInput(params \ "graph", baseDir + "graphs/", fileUtil)
  val relationMetadataDir = JsonHelper.getPathOrName(params, "relation metadata", baseDir, "relation_metadata").get
  val relationMetadataInput: (String, Option[Step]) = (relationMetadataDir, None)

  override val inputs = Set(splitInput, relationMetadataInput) ++ graphInput.toSet

  val weightFile = experimentDir + relation + "/weights.tsv"
  val featureMatrixFile = experimentDir + relation + "/training_matrix.tsv"
  val matrixOutput = if (outputFeatureMatrix) Set(featureMatrixFile) else Set()
  override val outputs = Set(weightFile) ++ matrixOutput
  override val inProgressFile = s"${experimentDir}${relation}/training_in_progress"
  override val paramFile = s"${experimentDir}${relation}/training_params.json"

  val split = Split.create(params \ "split", baseDir, fileUtil)
  val graph = Graph.create(params \ "graph", baseDir + "graphs/", fileUtil)
  val relationMetadata = RelationMetadata.create(params \ "relation metadata", baseDir, fileUtil)

  override def _runStep() {
    runTyped(split)
  }

  def getFeatureGenerator[T <: Instance](_split: Split[T]) = {
    FeatureGenerator.create(
      params \ "features",
      graph,
      _split,
      relation,
      relationMetadata,
      fileUtil = fileUtil
    )
  }

  def runTyped[T <: Instance](_split: Split[T]) {
    // I would like to make the featureGenerator a class member, but I can't, because of the type
    // inference.  I need to have a method that nails down the type of the split before I can
    // type-safely instantiate the feature generator.  That's the whole point of this runTyped
    // method - to nail down all of the types that depend on the split, which doesn't have a
    // concrete type as a class member.
    val featureGenerator = getFeatureGenerator(_split)
    val trainingData = _split.getTrainingData(relation, graph)
    val trainingMatrix = featureGenerator.createTrainingMatrix(trainingData)
    val featureNames = featureGenerator.getFeatureNames()
    if (outputFeatureMatrix) {
      logger.info("Saving feature matrix")
      Output.outputFeatureMatrix(featureMatrixFile, trainingMatrix, featureNames, fileUtil)
    }

    // Then we train a model.
    val model = BatchModel.create(params \ "learning", _split)
    model.train(trainingMatrix, trainingData, featureNames)
    model.saveState(weightFile, featureNames, fileUtil)
  }
}

class TopTenByPprScorer(
  params: JValue,
  baseDir: String,
  experimentDir: String,
  fileUtil: FileUtil
) extends Step(Some(params), fileUtil) with LazyLogging {
  implicit val formats = DefaultFormats
  override val name = "Top Ten By PPR Scorer"
  val validParams = Seq("training", "split", "graph", "relation metadata", "relation", "ppr computer")
  JsonHelper.ensureNoExtras(params, name, validParams)

  val trainer = new BatchTrainer(params \ "training", baseDir, experimentDir, fileUtil)
  val trainerInput = (trainer.weightFile, Some(trainer))

  // All of these options are necessary inputs to the trainer.  However, we're going to allow some
  // of them to also be overriden here, if desired.  This will let you train on one graph and test
  // on another, or train on one split and test on several others.  I'm not going to allow you to
  // use a different feature generator at test time than you do at training time, though, because
  // why would you want to do that?
  val relation = JsonHelper.extractAsOption[String](params, "relation") match {
    case None => trainer.relation
    case Some(r) => r
  }
  val split = (params \ "split") match {
    case JNothing => trainer.split.asInstanceOf[Split[NodePairInstance]]
    case jval => {
      Split.create(jval, baseDir, fileUtil).asInstanceOf[Split[NodePairInstance]]
    }
  }

  // This could be done at the same time as the split, but doing it this way avoids a compiler
  // warning about inferred existential types on the Split object.
  val splitInput = (params \ "split") match {
    case JNothing => trainer.splitInput
    case jval => {
      Split.getStepInput(params \ "split", baseDir, fileUtil)
    }
  }

  val (graph, graphInput) = (params \ "graph") match {
    case JNothing => (trainer.graph, trainer.graphInput)
    case jval => {
      val graph = Graph.create(jval, baseDir + "graphs/", fileUtil)
      val graphInput = Graph.getStepInput(jval, baseDir + "graphs/", fileUtil)
      (graph, graphInput)
    }
  }

  val (relationMetadata, relationMetadataInput) = (params \ "relation metadata") match {
    case JNothing => (trainer.relationMetadata, trainer.relationMetadataInput)
    case jval => {
      val relationMetadataDir = JsonHelper.getPathOrName(params, "relation metadata", baseDir, "relation_metadata").get
      val relationMetadataInput: (String, Option[Step]) = (relationMetadataDir, None)
      val relationMetadata = RelationMetadata.create(params \ "relation metadata", baseDir, fileUtil)
      (relationMetadata, relationMetadataInput)
    }
  }
  val pprComputer = PprComputer.create(params \ "ppr computer", graph.get, new scala.util.Random)

  override val inputs: Set[(String, Option[Step])] =
    Set(splitInput, relationMetadataInput, trainerInput) ++ graphInput.toSet

  val scoresFile = experimentDir + relation + "/scores.tsv"
  override val outputs = Set(scoresFile)
  override val inProgressFile = s"${experimentDir}${relation}/scoring_in_progress"
  override val paramFile = s"${experimentDir}${relation}/scoring_params.json"

  override def _runStep() {
    val modelFile = trainer.weightFile
    val featureDictionary = new MutableConcurrentDictionary
    val model = LogisticRegressionModel.loadFromFile[NodePairInstance](modelFile, featureDictionary, fileUtil)
    val featureGenerator = FeatureGenerator.create(
      params \ "trainer" \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      fileUtil = fileUtil
    )

    val allowedSources = relationMetadata.getAllowedSources(relation, graph)
    val allowedTargets = relationMetadata.getAllowedTargets(relation, graph)

    // We want to filter out training triples from the list of predictions.  We'll use this to do
    // that.
    val trainingData = split.getTrainingData(relation, graph).instances
      .map(_.asInstanceOf[NodePairInstance]).map(i => (i.source, i.target))
    val trainingSourceTargets = trainingData.groupBy(_._1).mapValues(_.map(_._2).toSet)
    val trainingTargetSources = trainingData.groupBy(_._2).mapValues(_.map(_._1).toSet)

    val testingData = split.getTestingData(relation, graph)

    val sources = testingData.instances.map(_.asInstanceOf[NodePairInstance].source).toSet -- Set(-1)
    val targets = testingData.instances.map(_.asInstanceOf[NodePairInstance].target).toSet -- Set(-1)
    val pprValues = pprComputer.computePersonalizedPageRank(
      sources,
      targets,
      allowedTargets,
      allowedSources  // yes, these two are flipped on purpose. See comments in PprComputer.
    )

    val predictions = testingData.instances.flatMap(instance => {
      val npi = instance.asInstanceOf[NodePairInstance]

      /*
      val originalSvoMatrixRow = generator.constructMatrixRow(npi)
      val originalSvoScore = originalSvoMatrixRow match {
        case None => 0.0
        case Some(matrixRow) => model.classifyMatrixRow(matrixRow)
      }
      val originalSvoLine = if (npi.isInGraph) s"${npi}\t${originalSvoScore}" else "instance not in graph..."
      fileUtil.writeLinesToFile(outputFile, Seq(originalSvoLine, ""), true)
      */

      val trainingTargets = trainingSourceTargets.getOrElse(npi.source, Set())
      val potentialTargets = pprValues.getOrElse(npi.source, Map()).filterNot(pprValue => {
        trainingTargets.contains(pprValue._1)
      })
      val targets = potentialTargets.toSeq.sortBy(-_._2).take(10).map(_._1)
      val svInstances = targets.map(t => new NodePairInstance(npi.source, t, true, npi.graph))
      val svScores = svInstances.par.map(instance => {
        val matrixRow = featureGenerator.constructMatrixRow(instance)
        val score = matrixRow match {
          case None => 0.0
          case Some(matrixRow) => model.classifyMatrixRow(matrixRow)
        }
        (score, instance)
      }).seq

      val trainingSources = trainingTargetSources.getOrElse(npi.target, Set())
      val potentialSources = pprValues.getOrElse(npi.target, Map()).filterNot(pprValue => {
        trainingTargets.contains(pprValue._1)
      })
      val sources = potentialSources.toSeq.sortBy(-_._2).take(10).map(_._1)
      val voInstances = sources.par.map(s => new NodePairInstance(s, npi.target, true, npi.graph))
      val voScores = voInstances.map(instance => {
        val matrixRow = featureGenerator.constructMatrixRow(instance)
        val score = matrixRow match {
          case None => 0.0
          case Some(matrixRow) => model.classifyMatrixRow(matrixRow)
        }
        (score, instance)
      }).seq
      svScores ++ voScores
    })
    // This funny business is because trying to put `predictions` directly into a set didin't work,
    // because Instance objects are only equal by reference (and need to stay that way for other
    // reasons).
    val instanceScores = predictions.map(_.swap).map(i => ((i._1.source, i._1.target), (i._2, i._1))).toMap
    val uniqueInstances = instanceScores.keys.toSet
    val uniquePredictions = uniqueInstances.map(i => instanceScores(i))

    // Finally, output a big list per relation.
    val lines = uniquePredictions.toSeq.sortBy(-_._1).map(x => s"${x._2}\t${x._1}")
    fileUtil.writeLinesToFile(scoresFile, lines)
  }
}

class TrainAndTest[T <: Instance](
  params: JValue,
  graph: Option[Graph],
  split: Split[T],
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val validParams = Seq("type", "features", "learning")
  JsonHelper.ensureNoExtras(params, "operation", validParams)

  override def runRelation(relation: String) {

    // First we get features.
    val generator = FeatureGenerator.create(
      params \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      fileUtil = fileUtil
    )

    val trainingData = split.getTrainingData(relation, graph)
    val trainingMatrix = generator.createTrainingMatrix(trainingData)
    //outputter.outputFeatureMatrix(true, trainingMatrix, generator.getFeatureNames())

    // Then we train a model.
    val model = BatchModel.create(params \ "learning", split)
    val featureNames = generator.getFeatureNames()
    model.train(trainingMatrix, trainingData, featureNames)

    // Then we test the model.
    val testingData = split.getTestingData(relation, graph)
    val testMatrix = generator.createTestMatrix(testingData)
    //outputter.outputFeatureMatrix(false, testMatrix, generator.getFeatureNames())
    val scores = model.classifyInstances(testMatrix)
    outputter.outputScores(scores, trainingData)
  }
}

class CreateMatrices[T <: Instance](
  params: JValue,
  praBase: String,
  graph: Option[Graph],
  split: Split[T],
  relationMetadata: RelationMetadata,
  fileUtil: FileUtil
) extends Operation[T] {
  val validParams = Seq("type", "features", "data")
  val dataOptions = Seq("both", "training", "testing")
  val dataToUse = JsonHelper.extractChoiceWithDefault(params, "data", dataOptions, "both")
  val experimentDir = praBase + "results/"

  override def runRelation(relation: String) {
    logger.info(s"Creating feature matrices for relation ${relation}; using data: ${dataToUse}")
    val generator = FeatureGenerator.create(
      params \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      fileUtil = fileUtil
    )

    if (dataToUse == "training" || dataToUse == "both") {
      val trainingData = split.getTrainingData(relation, graph)
      val trainingMatrix = generator.createTrainingMatrix(trainingData)
      val featureMatrixFile = experimentDir + relation + "/training_matrix.tsv"
      val featureNames = generator.getFeatureNames()
      Output.outputFeatureMatrix(featureMatrixFile, trainingMatrix, featureNames, fileUtil)
    }
    if (dataToUse == "testing" || dataToUse == "both") {
      val testingData = split.getTestingData(relation, graph)
      val testingMatrix = generator.createTestMatrix(testingData)
      val featureMatrixFile = experimentDir + relation + "/test_matrix.tsv"
      val featureNames = generator.getFeatureNames()
      Output.outputFeatureMatrix(featureMatrixFile, testingMatrix, featureNames, fileUtil)
    }
  }
}

class SgdTrainAndTest[T <: Instance](
  params: JValue,
  graph: Option[Graph],
  split: Split[T],
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val validParams = Seq("type", "learning", "features", "cache feature vectors")
  JsonHelper.ensureNoExtras(params, "operation", validParams)

  val cacheFeatureVectors = JsonHelper.extractWithDefault(params, "cache feature vectors", true)
  val random = new util.Random

  override def runRelation(relation: String) {
    val featureVectors = new concurrent.TrieMap[Instance, Option[MatrixRow]]

    val model = OnlineModel.create(params \ "learning")
    val generator = FeatureGenerator.create(
      params \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      fileUtil = fileUtil
    )

    val trainingData = split.getTrainingData(relation, graph)

    logger.info("Starting learning")
    val start = compat.Platform.currentTime
    for (iteration <- 1 to model.iterations) {
      logger.info(s"Iteration $iteration")
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
    logger.info(s"Learning took $seconds seconds")

    val featureNames = generator.getFeatureNames()
    outputWeights(model.getWeights(), featureNames)
    // TODO(matt): I should probably add something to the outputter to append to the matrix file,
    // or something, so I can have this call above and not have to keep around the feature vectors,
    // especially if I'm not caching them.  Same for the test matrix below.  I could also possibly
    // make the matrix a lazy parameter, hidden within a function call, so that I don't have to
    // compute it here if it's not going to be needed...
    val trainingMatrix = new FeatureMatrix(featureVectors.values.flatMap(_ match {
      case Some(row) => Seq(row)
      case _ => Seq()
    }).toList.asJava)
    //outputter.outputFeatureMatrix(true, trainingMatrix, generator.getFeatureNames())

    featureVectors.clear

    // Now we test the model.
    val testingData = split.getTestingData(relation, graph)
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
    //outputter.outputFeatureMatrix(false, testingMatrix, generator.getFeatureNames())
  }

  def outputWeights(weights: Seq[Double], featureNames: Seq[String]) {
    val filename = "weights.tsv" // TODO(matt): FIX ME! baseDir + relation + "/weights.tsv"
    val lines = weights.zip(featureNames).sortBy(-_._1).map(weight => {
      s"${weight._2}\t${weight._1}"
    })
    fileUtil.writeLinesToFile(filename, lines)
  }
}
