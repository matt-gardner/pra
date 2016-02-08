package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.features.extractors.EmptyFeatureMatcher
import edu.cmu.ml.rtw.pra.features.extractors.FeatureMatcher
import edu.cmu.ml.rtw.pra.features.extractors.NodePairFeatureExtractor
import edu.cmu.ml.rtw.pra.features.extractors.PraFeatureExtractor
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import org.scalatest._

import org.json4s._

// This test looks methods that are specific to NodePairSubgraphFeatureGenerator, which is
// currently the getRelatedEntities and findMatchingEntites methods.
class NodePairSubgraphFeatureGeneratorSpec extends FlatSpecLike with Matchers {

  val outputter = Outputter.justLogger
  val metadata = RelationMetadata.empty

  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead("/graph/node_dict.tsv",
    "1\tnode1\n2\tnode2\n3\tnode3\n4\tnode4\n5\t100\n6\t50\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv",
    "1\trel1\n2\trel2\n3\trel3\n4\trel4\n5\t@ALIAS@\n")
  val graphFile = "/graph/graph_chi/edges.tsv"
  val graphFileContents = "1\t2\t1\n" +
    "1\t3\t3\n" +
    "2\t3\t2\n" +
    "2\t4\t2\n" +
    "2\t5\t3\n" +
    "3\t2\t2\n"
  fileUtil.addFileToBeRead(graphFile, graphFileContents)
  val graph = new GraphOnDisk("/graph/", outputter, fileUtil)
  val relation = "rel3"

  "getRelatedEntities" should "get matchers from the feature extractors, then union the results" in {
    // A lot of set up for this simple test...
    val matchers = Seq(
      new EmptyFeatureMatcher[NodePairInstance],
      new EmptyFeatureMatcher[NodePairInstance]
    )
    val extractors = Seq(
      new NodePairFeatureExtractor {
        override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = null
        override def getFeatureMatcher(feature: String, isSource: Boolean, graph: Graph) = Some(matchers(0))
      },
      new NodePairFeatureExtractor {
        override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = null
        override def getFeatureMatcher(feature: String, isSource: Boolean, graph: Graph) = Some(matchers(1))
      },
      new NodePairFeatureExtractor {
        override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = null
        override def getFeatureMatcher(feature: String, isSource: Boolean, graph: Graph) = None
      }
    )
    val generator = new NodePairSubgraphFeatureGenerator(JNothing, relation, metadata, outputter) {
      override def createExtractors(params: JValue) = extractors
      override def findMatchingNodes(
        node: String,
        featureMatcher: FeatureMatcher[NodePairInstance],
        graph: Graph
      ): Set[String] = {
        featureMatcher match {
          case m if m == matchers(0) => Set("node 1", "node 2")
          case m if m == matchers(1) => Set("node 2", "node 3")
        }
      }
    }

    // Now the actual test.
    generator.getRelatedNodes("ignored", true, Seq("ignored"), graph) should be(
      Set("node 1", "node 2", "node 3"))
  }

  "findMatchingNodes" should "find the right nodes with a PraFeatureExtractor matcher" in {
    val generator = new NodePairSubgraphFeatureGenerator(JNothing, relation, metadata, outputter)
    val matcher1 = new PraFeatureExtractor().getFeatureMatcher("-rel1-rel2-", true, graph).get
    generator.findMatchingNodes("node1", matcher1, graph) should be(Set("node3", "node4"))
    generator.findMatchingNodes("node2", matcher1, graph) should be(Set())
    generator.findMatchingNodes("node3", matcher1, graph) should be(Set())
    generator.findMatchingNodes("unseen node", matcher1, graph) should be(Set())
    val matcher2 = new PraFeatureExtractor().getFeatureMatcher("-rel3-", false, graph).get
    generator.findMatchingNodes("node1", matcher2, graph) should be(Set())
    generator.findMatchingNodes("node2", matcher2, graph) should be(Set())
    generator.findMatchingNodes("node3", matcher2, graph) should be(Set("node1"))
    generator.findMatchingNodes("100", matcher2, graph) should be(Set("node2"))
    generator.findMatchingNodes("unseen node", matcher2, graph) should be(Set())
    val matcher3 = new PraFeatureExtractor().getFeatureMatcher("-rel1-_rel2-", true, graph).get
    generator.findMatchingNodes("node1", matcher3, graph) should be(Set("node3"))
    generator.findMatchingNodes("node2", matcher3, graph) should be(Set())
    generator.findMatchingNodes("unseen node", matcher3, graph) should be(Set())
  }
}
