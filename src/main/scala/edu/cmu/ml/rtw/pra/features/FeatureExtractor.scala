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

class OneSidedFeatureExtractor(edgeDict: Dictionary, nodeDict: Dictionary) extends FeatureExtractor {
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    subgraph.asScala.flatMap(entry => {
      entry._2.asScala.map(nodePair => {
        val path = entry._1.encodeAsHumanReadableString(edgeDict)
        val endNode = nodeDict.getString(nodePair.getRight)
        if (nodePair.getLeft == source) {
          s"SOURCE:${path}:${endNode}"
        } else if (nodePair.getLeft == target) {
          s"TARGET:${path}:${endNode}"
        } else {
          throw new IllegalStateException("Something is wrong with the subgraph - " +
            "the first node should always be either the source or the target")
        }
      })
    }).toList.asJava
  }
}
