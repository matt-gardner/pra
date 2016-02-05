package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.features.Subgraph
import edu.cmu.ml.rtw.pra.graphs.Graph

trait FeatureExtractor[T <: Instance] {
  def extractFeatures(instance: T, subgraph: Subgraph): Seq[String]

  // This essentially goes backward.  FeatureExtractor.extractFeatures lets you take a subgraph and
  // get features out.  This method takes features in, then lets you filter the graph to only
  // subgraphs that would have generated this feature.
  def getFeatureMatcher(feature: String, graph: Graph): Option[FeatureMatcher[T]]
}
