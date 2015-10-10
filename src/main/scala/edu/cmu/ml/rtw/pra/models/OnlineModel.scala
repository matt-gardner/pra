package edu.cmu.ml.rtw.pra.models

import edu.cmu.ml.rtw.pra.features.MatrixRow

trait OnlineModel {
  def updateWeights(instance: MatrixRow)

  def classifyInstance(instance: MatrixRow)
}
