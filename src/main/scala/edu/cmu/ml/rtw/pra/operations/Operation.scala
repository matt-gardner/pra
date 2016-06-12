package edu.cmu.ml.rtw.pra.operations

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.Split
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

import com.mattg.util.Dictionary
import com.mattg.util.FileUtil
import com.mattg.util.JsonHelper
import com.mattg.util.MutableConcurrentDictionary

import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.collection.mutable

import org.json4s._
import org.json4s.JsonDSL._

trait Operation[T <: Instance] {
  def runRelation(relation: String)
}

object Operation {
  // I had this returning an Option[Operation[T]], and I liked that, because it removes the need
  // for NoOp.  However, it gave me a compiler warning about inferred existential types, and
  // leaving it without the Option doesn't give me that warning.  So, I'll take the slightly more
  // ugly design instead of the compiler warning.
  def create[T <: Instance](
    params: JValue,
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
        new CreateMatrices(params, graph, split, relationMetadata, outputter, fileUtil)
      case "sgd train and test" =>
        new SgdTrainAndTest(params, graph, split, relationMetadata, outputter, fileUtil)
      case "hacky hanie operation" =>
        split match {
          case s: NodePairSplit => {
            new HackyHanieOperation(params, graph, split, relationMetadata, outputter, fileUtil)
          }
          case _ => throw new IllegalStateException("can only use this operation with a node pair split")
        }
      case other => throw new IllegalStateException(s"Unrecognized operation: $other")
    }
  }
}

class NoOp[T <: Instance] extends Operation[T] {
  override def runRelation(relation: String) { }
}

class TrainAndTest[T <: Instance](
  params: JValue,
  graph: Option[Graph],
  split: Split[T],
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "features", "learning")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  override def runRelation(relation: String) {

    // First we get features.
    val generator = FeatureGenerator.create(
      params \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      outputter,
      fileUtil = fileUtil
    )

    val trainingData = split.getTrainingData(relation, graph)
    val trainingMatrix = generator.createTrainingMatrix(trainingData)
    outputter.outputFeatureMatrix(true, trainingMatrix, generator.getFeatureNames())

    // Then we train a model.
    val model = BatchModel.create(params \ "learning", split, outputter)
    val featureNames = generator.getFeatureNames()
    model.train(trainingMatrix, trainingData, featureNames)

    // Then we test the model.
    val testingData = split.getTestingData(relation, graph)
    val testMatrix = generator.createTestMatrix(testingData)
    outputter.outputFeatureMatrix(false, testMatrix, generator.getFeatureNames())
    val scores = model.classifyInstances(testMatrix)
    outputter.outputScores(scores, trainingData)
  }
}

class HackyHanieOperation(
  params: JValue,
  graph: Option[Graph],
  split: Split[NodePairInstance],
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[NodePairInstance] {
  val paramKeys = Seq("type", "features")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  override def runRelation(relation: String) {

    // TODO(matt): VERY BAD!  But this should be fixable once I make these into Steps.
    val modelFile = s"/home/mattg/pra/results/animal/sfe/$relation/weights.tsv"
    val featureDictionary = new MutableConcurrentDictionary
    val model = LogisticRegressionModel.loadFromFile[NodePairInstance](modelFile, featureDictionary, outputter, fileUtil)
    runRelationWithModel(relation, model, featureDictionary)
  }

  def runRelationWithModel(
    relation: String,
    model: LogisticRegressionModel[NodePairInstance],
    featureDictionary: MutableConcurrentDictionary
  ) {
    val generator = FeatureGenerator.create(
      params \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      outputter,
      featureDictionary,
      fileUtil = fileUtil
    )

    val pprParams: JValue = ("walks per source" -> 50) ~ ("num steps" -> 3)
    val pprComputer = PprComputer.create(pprParams, graph.get, outputter, new scala.util.Random)

    val outputFile = outputter.baseDir + relation + "/scores.tsv"
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
        val matrixRow = generator.constructMatrixRow(instance)
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
        val matrixRow = generator.constructMatrixRow(instance)
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
    fileUtil.writeLinesToFile(outputFile, lines)
  }
}

class CreateMatrices[T <: Instance](
  params: JValue,
  graph: Option[Graph],
  split: Split[T],
  relationMetadata: RelationMetadata,
  outputter: Outputter,
  fileUtil: FileUtil
) extends Operation[T] {
  val paramKeys = Seq("type", "features", "data")
  val dataOptions = Seq("both", "training", "testing")
  val dataToUse = JsonHelper.extractChoiceWithDefault(params, "data", dataOptions, "both")

  override def runRelation(relation: String) {
    outputter.info(s"Creating feature matrices for relation ${relation}; using data: ${dataToUse}")
    val generator = FeatureGenerator.create(
      params \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      outputter,
      fileUtil = fileUtil
    )

    if (dataToUse == "training" || dataToUse == "both") {
      val trainingData = split.getTrainingData(relation, graph)
      val trainingMatrix = generator.createTrainingMatrix(trainingData)
      outputter.outputFeatureMatrix(true, trainingMatrix, generator.getFeatureNames())
    }
    if (dataToUse == "testing" || dataToUse == "both") {
      val testingData = split.getTestingData(relation, graph)
      val testingMatrix = generator.createTestMatrix(testingData)
      outputter.outputFeatureMatrix(false, testingMatrix, generator.getFeatureNames())
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
  val paramKeys = Seq("type", "learning", "features", "cache feature vectors")
  JsonHelper.ensureNoExtras(params, "operation", paramKeys)

  val cacheFeatureVectors = JsonHelper.extractWithDefault(params, "cache feature vectors", true)
  val random = new util.Random

  override def runRelation(relation: String) {
    val featureVectors = new concurrent.TrieMap[Instance, Option[MatrixRow]]

    val model = OnlineModel.create(params \ "learning", outputter)
    val generator = FeatureGenerator.create(
      params \ "features",
      graph,
      split,
      relation,
      relationMetadata,
      outputter,
      fileUtil = fileUtil
    )

    val trainingData = split.getTrainingData(relation, graph)

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
    outputter.outputFeatureMatrix(false, testingMatrix, generator.getFeatureNames())
  }
}
