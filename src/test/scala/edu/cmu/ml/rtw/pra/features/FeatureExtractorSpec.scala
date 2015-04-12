package edu.cmu.ml.rtw.pra.features

import java.io.BufferedReader
import java.io.StringReader

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.DatasetFactory
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

class FeatureExtractorSpec extends FlatSpecLike with Matchers {
  val factory = new BasicPathTypeFactory
  val edgeDict = new Dictionary
  edgeDict.getIndex("rel1")
  edgeDict.getIndex("rel2")
  val nodeDict = new Dictionary
  nodeDict.getIndex("node1")
  nodeDict.getIndex("node2")
  nodeDict.getIndex("node3")
  nodeDict.getIndex("node4")

  def getSubgraph(pathTypes: Seq[String], nodePairs: Seq[Set[(Int, Int)]]) = {
    val subgraph = new java.util.HashMap[PathType, java.util.Set[Pair[Integer, Integer]]]
    for (entry <- pathTypes.zip(nodePairs)) {
      val pathType = factory.fromString(entry._1)
      val pairs = new java.util.HashSet[Pair[Integer, Integer]]
      for (pair <- entry._2) {
        pairs.add(Pair.makePair(Integer.valueOf(pair._1), Integer.valueOf(pair._2)))
      }
      subgraph.put(pathType, pairs)
    }
    subgraph
  }

  "PraFeatureExtractor" should "extract only standard PRA features" in {
    val pathTypes = Seq("-1-", "-2-")
    val nodePairs = Seq(Set((1, 2), (1, 3)), Set((1, 3)))
    val extractor = new PraFeatureExtractor(edgeDict)
    val features = extractor.extractFeatures(1, 2, getSubgraph(pathTypes, nodePairs)).asScala
    features.size should be(1)
    features should contain("-rel1-")
  }


  "OneSidedFeatureExtractor" should "map each path type entry to a one-sided feature" in {
    val pathTypes = Seq("-1-", "-2-")
    val nodePairs = Seq(Set((1, 2), (2, 3)), Set((1, 3)))
    val extractor = new OneSidedFeatureExtractor(edgeDict, nodeDict)
    val features = extractor.extractFeatures(1, 2, getSubgraph(pathTypes, nodePairs)).asScala
    features.size should be(3)
    features should contain("SOURCE:-rel1-:node2")
    features should contain("TARGET:-rel1-:node3")
    features should contain("SOURCE:-rel2-:node3")
  }
}
