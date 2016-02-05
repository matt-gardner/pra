package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.features.extractors.EmptyNodePairFeatureMatcher
import edu.cmu.ml.rtw.pra.features.extractors.FeatureMatcher
import edu.cmu.ml.rtw.pra.features.extractors.NodePairFeatureExtractor
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk

import org.scalatest._

import org.json4s._

// This test looks methods that are specific to NodePairSubgraphFeatureGenerator, which is
// currently the getRelatedEntities and findMatchingEntites methods.
class NodePairSubgraphFeatureGeneratorSpec extends FlatSpecLike with Matchers {

  val outputter = Outputter.justLogger
  val metadata = RelationMetadata.empty

  val graph = new GraphOnDisk("src/test/resources/", outputter)
  val relation = "rel3"

  "getRelatedEntities" should "get matchers from the feature extractors, then union the results" in {
    // A lot of set up for this simple test...
    val matchers = Seq(EmptyNodePairFeatureMatcher, EmptyNodePairFeatureMatcher)
    val extractors = Seq(
      new NodePairFeatureExtractor {
        override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = null
        override def getFeatureMatcher(feature: String, graph: Graph) = Some(matchers(0))
      },
      new NodePairFeatureExtractor {
        override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = null
        override def getFeatureMatcher(feature: String, graph: Graph) = Some(matchers(1))
      },
      new NodePairFeatureExtractor {
        override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = null
        override def getFeatureMatcher(feature: String, graph: Graph) = None
      }
    )
    val generator = new NodePairSubgraphFeatureGenerator(JNothing, relation, metadata, outputter) {
      override def createExtractors(params: JValue) = extractors
      override def findMatchingEntities(
        entity: String,
        featureMatcher: FeatureMatcher[NodePairInstance],
        graph: Graph
      ): Set[String] = {
        featureMatcher match {
          case m if m == matchers(0) => Set("entity 1", "entity 2")
          case m if m == matchers(1) => Set("entity 2", "entity 3")
        }
      }
    }

    // Now the actual test.
    generator.getRelatedEntities("ignored", Seq("ignored"), graph) should be(
      Set("entity 1", "entity 2", "entity 3"))
  }
}
