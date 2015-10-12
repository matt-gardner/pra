package edu.cmu.ml.rtw.pra.models

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.users.matt.util.JsonHelper

import org.json4s._

trait OnlineModel {
  def iterations(): Int

  // NOTE: This will get called in _parallel_, so it probably needs to synchronize on something.
  def updateWeights(instance: MatrixRow)

  // This will also be called in parallel, but that shouldn't matter.
  def classifyInstance(instance: MatrixRow): Double
}

class SgdLogisticRegressionModel(params: JValue, config: PraConfig) extends OnlineModel {
  val paramKeys = Seq("type", "learning", "iterations")
  JsonHelper.ensureNoExtras(params, "SgdLogisticRegressionModel", paramKeys)

  val _iterations = JsonHelper.extractWithDefault(params, "iterations", 10)

  def iterations() = _iterations

  def updateWeights(instance: MatrixRow) { }

  def classifyInstance(instance: MatrixRow): Double = { 0.0 }
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
