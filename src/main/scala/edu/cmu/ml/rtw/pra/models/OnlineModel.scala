package edu.cmu.ml.rtw.pra.models

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.MatrixRow
import com.mattg.util.JsonHelper

import org.json4s._

trait OnlineModel {
  def iterations(): Int

  def getWeights(): Seq[Double]

  // This is to tell the learning that you've finished an iteration and are starting the next one,
  // in case learning rates need to be updated.
  def nextIteration()

  // NOTE: This will get called in _parallel_, so it probably needs to synchronize on something.
  def updateWeights(instance: MatrixRow)

  // This will also be called in parallel, but that shouldn't matter.
  def classifyInstance(instance: MatrixRow): Double
}

class SgdLogisticRegressionModel[T <: Instance](
  params: JValue,
  outputter: Outputter
) extends OnlineModel {
  val paramKeys = Seq("type", "learning rate a", "learning rate b", "learning rate momentum",
    "l2 weight", "iterations")
  JsonHelper.ensureNoExtras(params, "SgdLogisticRegressionModel", paramKeys)

  val _iterations = JsonHelper.extractWithDefault(params, "iterations", 20)
  // TODO(matt): implement L1 weights, too.  Shouldn't be that bad, I don't think.
  val l2Weight = JsonHelper.extractWithDefault(params, "l2 weight", 0.0)

  val learningRateA = JsonHelper.extractWithDefault(params, "learning rate a", 1000.0)
  val learningRateB = JsonHelper.extractWithDefault(params, "learning rate b", 1000.0)
  val learningRateMomentum = JsonHelper.extractWithDefault(params, "learning rate momentum", 0.5)

  var numFeatures = 50000
  var weights = new Array[Double](numFeatures)
  var velocity = new Array[Double](numFeatures)
  var lastUpdated = new Array[Int](numFeatures)
  var k = 0
  var iteration = 0
  var logConditionalLikelihood = 0.0

  val overflow = 20

  def iterations() = _iterations

  def getWeights() = weights

  def nextIteration() {
    iteration += 1
    outputter.info(s"LCL at iteration ${iteration - 1}: ${logConditionalLikelihood}")
    logConditionalLikelihood = 0.0
  }

  def sigmoid(x: Double) = {
    val exp = if (x > overflow) {
      Math.exp(overflow)
    } else if (x < -overflow) {
      Math.exp(-overflow)
    } else {
      Math.exp(x)
    }
    exp / (1 + exp)
  }

  def growFeatures(newSize: Int) {
    numFeatures = newSize
    val newWeights = new Array[Double](numFeatures)
    val newVelocity = new Array[Double](numFeatures)
    val newLastUpdated = new Array[Int](numFeatures)
    Array.copy(weights, 0, newWeights, 0, weights.length)
    Array.copy(lastUpdated, 0, newLastUpdated, 0, lastUpdated.length)
    Array.copy(velocity, 0, newVelocity, 0, velocity.length)
    weights = newWeights
    lastUpdated = newLastUpdated
    velocity = newVelocity
  }

  def computeLambda(): Double = {
    learningRateA / (learningRateB + k)
  }

  def updateWeights(instance: MatrixRow) {
    // I'm thinking that the majority of the computation happens in doing a search over the graph,
    // not in updating the weights.  This part should be pretty fast.  So I'm just synchronizing
    // the whole thing.  If this gets to be a problem, I could, e.g., make a new thread that
    // accepts matrix rows from a queue, so that other threads can just add things to the queue and
    // keep doing searches.
    this.synchronized {
      val lambda = computeLambda()

      // Update with regularization portion of the weights.
      for (feature <- instance.featureTypes) {
        if (feature >= numFeatures) growFeatures(feature * 2)
        val exponent = k - lastUpdated(feature)
        if (exponent >= 0) {
          val update = Math.pow(1 - 2 * lambda * l2Weight, exponent)
          weights(feature) *= update
          lastUpdated(feature) = k
        }
      }

      // Calculate the actual probability.
      val p = sigmoid(classifyInstance(instance))
      val y = if (instance.instance.isPositive) 1 else 0
      if (instance.instance.isPositive) {
        logConditionalLikelihood += Math.log(p)
      } else {
        logConditionalLikelihood += Math.log(1 - p)
      }

      // Updating the weights (LCL portion of the update).
      for (feature <- instance.featureTypes) {
        // We'll keep the velocity out of the regularization update for now...  Still testing this
        // to see how things work.
        velocity(feature) = learningRateMomentum * velocity(feature) + lambda * (y - p)
        weights(feature) += velocity(feature)
      }

      k += 1
    }
  }

  def classifyInstance(instance: MatrixRow): Double = {
    var score = 0.0
    for (feature <- instance.featureTypes) {
      if (feature < numFeatures) {
        score += weights(feature)
      }
    }
    return score
  }
}

object OnlineModel{
  def create[T <: Instance](params: JValue, outputter: Outputter): OnlineModel = {
    val modelType = JsonHelper.extractWithDefault(params, "type", "logistic regression")
    modelType match {
      case "logistic regression" => new SgdLogisticRegressionModel(params, outputter)
      case other => throw new IllegalStateException("Unrecognized model type")
    }
  }
}
