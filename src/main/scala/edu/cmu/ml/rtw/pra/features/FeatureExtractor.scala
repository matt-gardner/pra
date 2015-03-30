package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.Pair

import scala.collection.JavaConverters._

trait FeatureExtractor {
  type Subgraph = java.util.Map[PathType, java.util.Set[Pair[Integer, Integer]]]
  def extractFeatures(source: Int, target: Int, subgraph: Subgraph): java.util.List[String]
}

class PraFeatureExtractor(edgeDict: Dictionary)  extends FeatureExtractor {
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    val sourceTarget = new Pair[Integer, Integer](source, target)
    subgraph.asScala.flatMap(entry => {
      if (entry._2.contains(sourceTarget)) {
        List(entry._1.encodeAsHumanReadableString(edgeDict))
      } else {
        List[String]()
      }
    }).toList.asJava
  }
}
