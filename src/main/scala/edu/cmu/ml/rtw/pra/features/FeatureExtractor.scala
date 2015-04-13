package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.Pair

import scala.collection.JavaConverters._
import scala.util.control.Exception.allCatch
import scala.math.log10
import scala.math.abs

trait FeatureExtractor {
  type Subgraph = java.util.Map[PathType, java.util.Set[Pair[Integer, Integer]]]
  def extractFeatures(source: Int, target: Int, subgraph: Subgraph): java.util.List[String]
}

class PraFeatureExtractor(val edgeDict: Dictionary)  extends FeatureExtractor {
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

class OneSidedFeatureExtractor(val edgeDict: Dictionary, val nodeDict: Dictionary) extends FeatureExtractor {
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

class CategoricalComparisonFeatureExtractor(val edgeDict: Dictionary, val nodeDict: Dictionary) extends FeatureExtractor{
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    subgraph.asScala.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(edgeDict)
      val (src, targ) = entry._2.asScala.partition(nodePair => nodePair.getLeft == source)
      val pairs = for (int1 <- src; int2 <- targ) yield (nodeDict.getString(int1.getRight), nodeDict.getString(int2.getRight));
      for{pair <- pairs}  yield s"CATCOMP:${path}:${pair._1}:${pair._2}"     
    }).toList.asJava
  }
}

class NumericalComparisonFeatureExtractor(val edgeDict: Dictionary, val nodeDict: Dictionary) extends FeatureExtractor{
  override def extractFeatures(source: Int, target: Int, subgraph: Subgraph) = {
    subgraph.asScala.flatMap(entry => {
      val path = entry._1.encodeAsHumanReadableString(edgeDict)
      val (src, targ) = entry._2.asScala.partition(nodePair => nodePair.getLeft == source)
      val strings = for{int1 <- src; int2 <- targ} 
                      yield (nodeDict.getString(int1.getRight), nodeDict.getString(int2.getRight))
      val valid_strings = strings.filter(str => isDoubleNumber(str._1) && isDoubleNumber(str._2))
      //val valid_strings = strings.map(str => ("100", "50"))
      for(str <- valid_strings )  
        yield s"NUMCOMP:${path}:" + "%.2f".format(log10(abs(str._1.toDouble - str._2.toDouble))).toDouble
      
    }).toList.asJava
  }
  
  def isDoubleNumber(s: String): Boolean = (allCatch opt s.toDouble).isDefined
}
