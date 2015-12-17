package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.mutable

import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

class FeatureExtractorSpec extends FlatSpecLike with Matchers {
  val factory = new BasicPathTypeFactory
  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead("/graph/node_dict.tsv",
    "1\tnode1\n2\tnode2\n3\tnode3\n4\tnode4\n5\t100\n6\t50\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv",
    "1\trel1\n2\trel2\n3\trel3\n4\trel4\n5\t@ALIAS@\n")
  val graph = new GraphOnDisk("/graph/", fileUtil)
  val config = new PraConfigBuilder[NodePairInstance]().setPraBase("/").setNoChecks().build()

  val instance = new NodePairInstance(1, 2, true, graph)

  def getSubgraph(pathTypes: Seq[String], nodePairs: Seq[Set[(Int, Int)]]) = {
    val subgraph = new mutable.HashMap[PathType, Set[(Int, Int)]]
    for (entry <- pathTypes.zip(nodePairs)) {
      val pathType = factory.fromString(entry._1)
      subgraph.put(pathType, entry._2)
    }
    subgraph.toMap
  }

  "PraFeatureExtractor" should "extract only standard PRA features" in {
    val pathTypes = Seq("-1-", "-2-")
    val nodePairs = Seq(Set((1, 2), (1, 3)), Set((1, 3)))
    val extractor = new PraFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    features.size should be(1)
    features should contain("-rel1-")
  }

  "PathBigramsFeatureExtractor" should "extract bigrams from standard PRA features" in {
    val pathTypes = Seq("-1-2-", "-2-")
    val nodePairs = Seq(Set((1, 2), (1, 3)), Set((1, 3)))
    val extractor = new PathBigramsFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    features.size should be(3)
    features should contain("BIGRAM:@START@-rel1")
    features should contain("BIGRAM:rel1-rel2")
    features should contain("BIGRAM:rel2-@END@")
  }

  "OneSidedFeatureExtractor" should "map each path type entry to a one-sided feature" in {
    val pathTypes = Seq("-1-", "-2-")
    val nodePairs = Seq(Set((1, 2), (2, 3)), Set((1, 3)))
    val extractor = new OneSidedFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    print(features)
    features.size should be(3)
    features should contain("SOURCE:-rel1-:node2")
    features should contain("TARGET:-rel1-:node3")
    features should contain("SOURCE:-rel2-:node3")
  }

  "CategoricalComparisonFeatureExtractor" should "extract categorical comparison features" in {
    val pathTypes = Seq("-1-", "-2-")
    val nodePairs = Seq(Set((1,2)), Set((1,3),(2,4)))
    val extractor = new CategoricalComparisonFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    features.size should be(1)
    features should contain("CATCOMP:-rel2-:node3:node4")
  }

  "NumericalComparisonFeatureExtractor" should "extract numerical comparison features" in {
    val pathTypes = Seq("-1-", "-2-")
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val extractor = new NumericalComparisonFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    features.size should be(1)
    features should contain("NUMCOMP:-rel2-:1.7")  // log10(50) == 1.7
  }

  "VectorSimilarityFeatureExtractor" should "extract vector similarity features" in {
    val fileUtil = new FakeFileUtil
    val matrixFile = "/embeddings/test/matrix.tsv"
    fileUtil.addFileToBeRead(matrixFile, "rel1\trel2\t.9\nrel3\trel4\t.8\nrel1\trel3\t.7\n")
    val pathTypes = Seq("-1-3-", "-2-")
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val jval: JValue =
      ("name" -> "VectorSimilarityFeatureExtractor") ~
      ("matrix name" -> "test") ~
      ("max similar vectors" -> 10)
    val extractor = new VectorSimilarityFeatureExtractor(jval, config, fileUtil)
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    println(features)
    features.size should be(6)
    features should contain("VECSIM:-rel1-rel3-")
    features should contain("VECSIM:-rel2-rel3-")
    features should contain("VECSIM:-rel3-rel3-")
    features should contain("VECSIM:-rel1-rel4-")
    features should contain("VECSIM:-@ANY_REL@-rel3-")
    features should contain("VECSIM:-rel1-@ANY_REL@-")
  }

  it should "only use max similar vectors" in {
    val fileUtil = new FakeFileUtil
    val matrixFile = "/embeddings/test/matrix.tsv"
    fileUtil.addFileToBeRead(matrixFile, "rel1\trel2\t.9\nrel3\trel4\t.8\nrel1\trel3\t.7\n")
    val pathTypes = Seq("-1-3-", "-2-")
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val jval: JValue =
      ("name" -> "VectorSimilarityFeatureExtractor") ~
      ("matrix name" -> "test") ~
      ("max similar vectors" -> 1)
    val extractor = new VectorSimilarityFeatureExtractor(jval, config, fileUtil)
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    println(features)
    features.size should be(5)
    features should contain("VECSIM:-rel1-rel3-")
    features should contain("VECSIM:-rel2-rel3-")
    features should contain("VECSIM:-rel1-rel4-")
    features should contain("VECSIM:-@ANY_REL@-rel3-")
    features should contain("VECSIM:-rel1-@ANY_REL@-")
  }

  "AnyRelFeatureExtractor" should "replace each index with @ANY_REL@" in {
    val pathTypes = Seq("-1-3-", "-2-")
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val extractor = new AnyRelFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    println(features)
    features.size should be(2)
    features should contain("ANYREL:-@ANY_REL@-rel3-")
    features should contain("ANYREL:-rel1-@ANY_REL@-")
  }

  "AnyRelAliasOnlyFeatureExtractor" should "replace only paths with @ALIAS@" in {
    val pathTypes = Seq("-1-3-", "-2-", "-5-1-5-", "-5-1-2-5-")
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)), Set((1,2)), Set((1,2)))
    val extractor = new AnyRelAliasOnlyFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(pathTypes, nodePairs))
    println(features)
    features.size should be(3)
    features should contain("ANYREL:-@ALIAS@-@ANY_REL@-@ALIAS@-")
    features should contain("ANYREL:-@ALIAS@-@ANY_REL@-rel2-@ALIAS@-")
    features should contain("ANYREL:-@ALIAS@-rel1-@ANY_REL@-@ALIAS@-")
  }
}
