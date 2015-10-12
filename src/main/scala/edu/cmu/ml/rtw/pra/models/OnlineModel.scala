package edu.cmu.ml.rtw.pra.models

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import org.json4s._

trait OnlineModel {
  def iterations(): Int

  // This is to tell the learning that you've finished an iteration and are starting the next one,
  // in case learning rates need to be updated.
  def nextIteration()

  // NOTE: This will get called in _parallel_, so it probably needs to synchronize on something.
  def updateWeights(instance: MatrixRow)

  // This will also be called in parallel, but that shouldn't matter.
  def classifyInstance(instance: MatrixRow): Double
}

class SgdLogisticRegressionModel(params: JValue, config: PraConfig) extends OnlineModel {
  val paramKeys = Seq("type", "learning rate", "l2 weight", "iterations")
  JsonHelper.ensureNoExtras(params, "SgdLogisticRegressionModel", paramKeys)

  val _iterations = JsonHelper.extractWithDefault(params, "iterations", 10)
  // TODO(matt): implement L1 weights, too.  Shouldn't be that bad, I don't think.
  val l2Weight = JsonHelper.extractWithDefault(params, "l2 weight", 0.0)
  val learningRate = JsonHelper.extractWithDefault(params, "learning rate", 1.0)

  var numFeatures = 50000
  var weights = new Array[Double](numFeatures)
  var lastUpdated = new Array[Int](numFeatures)
  var k = 0
  var iteration = 0
  var lambda = 0.0
  var logConditionalLikelihood = 0.0

  val overflow = 20

  def iterations() = _iterations

  def nextIteration() {
    iteration += 1
    lambda = learningRate / (iteration * iteration)
    println(s"LCL at iteration ${iteration - 1}: ${logConditionalLikelihood}")
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
    val newLastUpdated = new Array[Int](numFeatures)
    Array.copy(weights, 0, newWeights, 0, weights.length)
    Array.copy(lastUpdated, 0, newLastUpdated, 0, lastUpdated.length)
    weights = newWeights
    lastUpdated = newLastUpdated
  }

  def updateWeights(instance: MatrixRow) {
    // I'm thinking that the majority of the computation happens in doing a search over the graph,
    // not in updating the weights.  This part should be pretty fast.  So I'm just synchronizing
    // the whole thing.  If this gets to be a problem, I could, e.g., make a new thread that
    // accepts matrix rows from a queue, so that other threads can just add things to the queue and
    // keep doing searches.
    this.synchronized {
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
      val p = classifyInstance(instance)
      val y = if (instance.instance.isPositive) 1 else 0
      if (instance.instance.isPositive) {
        logConditionalLikelihood += Math.log(p)
      } else {
        logConditionalLikelihood += Math.log(1 - p)
      }

      // Updating the weights (LCL portion of the update).
      for (feature <- instance.featureTypes) {
        weights(feature) += lambda * (y - p)
      }

      k += 1
    }
  }

  def classifyInstance(instance: MatrixRow): Double = {
    var score = 0.0
    for (feature <- instance.featureTypes) {
      score += weights(feature)
    }
    return score
  }
}

object OnlineModel{
  def create(params: JValue, config: PraConfig): OnlineModel = {
    val modelType = JsonHelper.extractWithDefault(params, "type", "logistic regression")
    modelType match {
      case "logistic regression" => new SgdLogisticRegressionModel(params, config)
      case other => throw new IllegalStateException("Unrecognized model type")
    }
  }
}
