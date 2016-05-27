package edu.cmu.ml.rtw.pra.features.extractors

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.mutable

import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.LexicalizedPathTypeFactory
import edu.cmu.ml.rtw.pra.features.Path
import edu.cmu.ml.rtw.pra.features.PathTypeFactory
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk

import com.mattg.util.FakeFileUtil
import com.mattg.util.Pair
import com.mattg.util.TestUtil
import com.mattg.util.TestUtil.Function

class NodePairFeatureExtractorSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger
  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead("/graph/node_dict.tsv",
    "1\tnode1\n2\tnode2\n3\tnode3\n4\tnode4\n5\t100\n6\t50\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv",
    "1\trel1\n" +
    "2\trel2\n" +
    "3\trel3\n" +
    "4\trel4\n" +
    "5\t@ALIAS@\n" +
    "6\t/business/brand/colors\n" +
    "7\t/business/brand/owner_s\n")
  val graph = new GraphOnDisk("/graph/", outputter, fileUtil)

  val instance = new NodePairInstance(1, 2, true, graph)

  def getSubgraph(
    paths: Seq[Path],
    nodePairs: Seq[Set[(Int, Int)]]
  ) = {
    paths.zip(nodePairs).toMap
  }

  def getPath(edges: Seq[Int], reverses: Option[Seq[Boolean]] = None) = {
    Path(0, new Array[Int](edges.size), edges.toArray, reverses.map(_.toArray).getOrElse(new Array[Boolean](edges.size)))
  }

  "PraFeatureExtractor" should "extract only standard PRA features" in {
    val paths = Seq(getPath(Seq(1)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1, 2), (1, 3)), Set((1, 3)))
    val extractor = new PraFeatureExtractor(JNothing)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(1)
    features should contain("-rel1-")
  }

  it should "include nodes in the path type when requested" in {
    val paths = Seq(Path(1, Array(1, 2), Array(1, 2), Array(false, false)))
    val nodePairs = Seq(Set((1, 2)))
    val params: JValue = ("include nodes" -> true)
    val extractor = new PraFeatureExtractor(params)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(1)
    features should contain("-rel1->node1-rel2->")
  }

  it should "and not when include nodes is set to false" in {
    val paths = Seq(Path(0, Array(1, 0), Array(1, 2), Array(false, false)))
    val nodePairs = Seq(Set((1, 2)))
    val params: JValue = ("include nodes" -> false)
    val extractor = new PraFeatureExtractor(params)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(1)
    features should contain("-rel1-rel2-")
  }

  "PathBigramsFeatureExtractor" should "extract bigrams from standard PRA features" in {
    val paths = Seq(getPath(Seq(1, 2)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1, 2), (1, 3)), Set((1, 3)))
    val extractor = new PathBigramsFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(3)
    features should contain("BIGRAM:@START@-rel1")
    features should contain("BIGRAM:rel1-rel2")
    features should contain("BIGRAM:rel2-@END@")
  }

  "OneSidedPathAndEndNodeFeatureExtractor" should "map each path type entry to a one-sided feature" in {
    val paths = Seq(getPath(Seq(1)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1, 2), (2, 3)), Set((1, 3)))
    val extractor = new OneSidedPathAndEndNodeFeatureExtractor(outputter)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    print(features)
    features.size should be(3)
    features should contain("SOURCE:-rel1-:node2")
    features should contain("TARGET:-rel1-:node3")
    features should contain("SOURCE:-rel2-:node3")
  }

  "CategoricalComparisonFeatureExtractor" should "extract categorical comparison features" in {
    val paths = Seq(getPath(Seq(1)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1,2)), Set((1,3),(2,4)))
    val extractor = new CategoricalComparisonFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(1)
    features should contain("CATCOMP:-rel2-:node3:node4")
  }

  "NumericalComparisonFeatureExtractor" should "extract numerical comparison features" in {
    val paths = Seq(getPath(Seq(1)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val extractor = new NumericalComparisonFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(1)
    features should contain("NUMCOMP:-rel2-:1.7")  // log10(50) == 1.7
  }

  "VectorSimilarityFeatureExtractor" should "extract vector similarity features" in {
    val fileUtil = new FakeFileUtil
    val matrixFile = "/embeddings/test/matrix.tsv"
    fileUtil.addFileToBeRead(matrixFile, "rel1\trel2\t.9\nrel3\trel4\t.8\nrel1\trel3\t.7\n")
    val paths = Seq(getPath(Seq(1, 3)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val jval: JValue =
      ("name" -> "VectorSimilarityFeatureExtractor") ~
      ("matrix path" -> matrixFile) ~
      ("max similar vectors" -> 10)
    val extractor = new VectorSimilarityFeatureExtractor(jval, fileUtil)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
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
    val paths = Seq(getPath(Seq(1, 3)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val jval: JValue =
      ("name" -> "VectorSimilarityFeatureExtractor") ~
      ("matrix path" -> matrixFile) ~
      ("max similar vectors" -> 1)
    val extractor = new VectorSimilarityFeatureExtractor(jval, fileUtil)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(5)
    features should contain("VECSIM:-rel1-rel3-")
    features should contain("VECSIM:-rel2-rel3-")
    features should contain("VECSIM:-rel1-rel4-")
    features should contain("VECSIM:-@ANY_REL@-rel3-")
    features should contain("VECSIM:-rel1-@ANY_REL@-")
  }

  "AnyRelFeatureExtractor" should "replace each index with @ANY_REL@" in {
    val paths = Seq(getPath(Seq(1, 3)), getPath(Seq(2)))
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)))
    val extractor = new AnyRelFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(2)
    features should contain("ANYREL:-@ANY_REL@-rel3-")
    features should contain("ANYREL:-rel1-@ANY_REL@-")
  }

  "AnyRelAliasOnlyFeatureExtractor" should "replace only paths with @ALIAS@" in {
    val paths = Seq(getPath(Seq(1, 3)), getPath(Seq(2)), getPath(Seq(5, 1, 5)), getPath(Seq(5, 1, 2, 5)))
    val nodePairs = Seq(Set((1,2)), Set((1,5),(2,6)), Set((1,2)), Set((1,2)))
    val extractor = new AnyRelAliasOnlyFeatureExtractor
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(3)
    features should contain("ANYREL:-@ALIAS@-@ANY_REL@-@ALIAS@-")
    features should contain("ANYREL:-@ALIAS@-@ANY_REL@-rel2-@ALIAS@-")
    features should contain("ANYREL:-@ALIAS@-rel1-@ANY_REL@-@ALIAS@-")
  }

  "ConnectedAtOneFeatureExtractor" should "use the provided feature name for one-hop connections" in {
    val paths = Seq(getPath(Seq(2)))
    val nodePairs = Seq(Set((1,2)))
    val params: JValue = ("feature name" -> "connected feature name")
    val extractor = new ConnectedAtOneFeatureExtractor(params)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(1)
    features should contain("connected feature name")
  }

  it should "not return anything when there is no direct connection" in {
    val paths = Seq(getPath(Seq(2, 3)))
    val nodePairs = Seq(Set((1,2)))
    val extractor = new ConnectedAtOneFeatureExtractor(JNothing)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(0)
  }

  it should "return the a matcher only when the feature matches" in {
    val params: JValue = ("feature name" -> "connected feature name")
    val extractor = new ConnectedAtOneFeatureExtractor(params)
    extractor.getFeatureMatcher("connected feature name", true, graph) should be(Some(ConnectedAtOneMatcher))
    extractor.getFeatureMatcher("connected feature name", false, graph) should be(Some(ConnectedAtOneMatcher))
    extractor.getFeatureMatcher("some other feature", true, graph) should be(None)
    extractor.getFeatureMatcher("some other feature", false, graph) should be(None)
  }

  "ConnectedByMediatorFeatureExtractor" should "not return anything for non-mediator connections" in {
    val paths = Seq(getPath(Seq(2, 3)), getPath(Seq(1)), getPath(Seq(1, 2, 3)), getPath(Seq(6, 7, 1), Some(Seq(false, true, false))))
    val nodePairs = Seq(Set((1,2)))
    val extractor = new ConnectedByMediatorFeatureExtractor(JNothing)
    val features = extractor.extractFeatures(instance, getSubgraph(paths, nodePairs))
    features.size should be(0)
  }

  it should "return the provided feature name when there is a path involving two mediator relations" in {
    val feature = "connected feature name"
    val params: JValue = ("feature name" -> feature)
    val extractor = new ConnectedByMediatorFeatureExtractor(params)

    // In a real Freebase graph you'll only actually see one of these possibilities (though which
    // one depends on which of the possible inverses is actually kept in the graph), assuming your
    // allowed sources and targets are not themselves mediators.  So our simple implementation
    // allows for all of these combinations, even though three of them wouldn't actually make sense
    // in a real graph.
    extractor.extractFeatures(instance, getSubgraph(Seq(getPath(Seq(6, 7))), Seq(Set((1, 2))))) should be(Seq(feature))
    extractor.extractFeatures(instance, getSubgraph(Seq(getPath(Seq(6, 7), Some(Seq(true, false)))), Seq(Set((1, 2))))) should be(Seq(feature))
    extractor.extractFeatures(instance, getSubgraph(Seq(getPath(Seq(6, 7), Some(Seq(false, true)))), Seq(Set((1, 2))))) should be(Seq(feature))
    extractor.extractFeatures(instance, getSubgraph(Seq(getPath(Seq(6, 7), Some(Seq(true, true)))), Seq(Set((1, 2))))) should be(Seq(feature))
  }

  it should "return the a matcher only when the feature matches" in {
    val params: JValue = ("feature name" -> "connected feature name")
    val extractor = new ConnectedByMediatorFeatureExtractor(params)
    extractor.getFeatureMatcher("connected feature name", true, graph).get shouldBe a [ConnectedByMediatorMatcher]
    extractor.getFeatureMatcher("connected feature name", false, graph).get shouldBe a [ConnectedByMediatorMatcher]
    extractor.getFeatureMatcher("some other feature", true, graph) should be(None)
    extractor.getFeatureMatcher("some other feature", false, graph) should be(None)
  }
}
