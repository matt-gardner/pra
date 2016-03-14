package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.features.Subgraph
import edu.cmu.ml.rtw.pra.graphs.Graph

trait FeatureExtractor[T <: Instance] {
  def extractFeatures(instance: T, subgraph: Subgraph): Seq[String]
}
