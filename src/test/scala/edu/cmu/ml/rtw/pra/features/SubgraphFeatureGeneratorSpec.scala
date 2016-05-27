package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.features.extractors.FeatureExtractor
import edu.cmu.ml.rtw.pra.features.extractors.PraFeatureExtractor
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.Dictionary
import com.mattg.util.FakeFileUtil
import com.mattg.util.Pair
import com.mattg.util.TestUtil
import com.mattg.util.TestUtil.Function

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._

class SubgraphFeatureGeneratorSpec extends FlatSpecLike with Matchers {

  val params: JValue = ("include bias" -> true)
  val outputter = Outputter.justLogger
  val graph = new GraphOnDisk("src/test/resources/", outputter)
  val fakeFileUtil = new FakeFileUtil
  val relation = "rel3"
  val metadata = RelationMetadata.empty

  val generator = new NodePairSubgraphFeatureGenerator(params, relation, metadata, outputter, fileUtil = fakeFileUtil)
  generator.featureDict.getIndex("feature1")
  generator.featureDict.getIndex("feature2")
  generator.featureDict.getIndex("feature3")

  def generatorWithParams(params: JValue) = {
    new NodePairSubgraphFeatureGenerator(params, relation, metadata, outputter, fileUtil = fakeFileUtil)
  }

  val instance = new NodePairInstance(1, 2, true, graph)
  val dataset = new Dataset[NodePairInstance](Seq(instance))

  "createMatrixFromData" should "call constructMatrixRow on all instances" in {
    val row = new MatrixRow(instance, Array[Int](), Array[Double]())
    val generator =
      new NodePairSubgraphFeatureGenerator(params, relation, metadata, outputter, fileUtil = fakeFileUtil) {
        override def constructMatrixRow(_instance: NodePairInstance, relation: String) = {
          if (_instance != instance) throw new RuntimeException()
          Some(row)
        }
      }
    val featureMatrix = generator.createTrainingMatrix(dataset, relation)
    featureMatrix.getRows().size should be(1)
    featureMatrix.getRow(0) should be(row)
  }

  "removeZeroWeightFeatures" should "not remove anything" in {
    val weights = Seq(1.0, 2.0, 3.0)
    generator.removeZeroWeightFeatures(weights) should be(weights)
  }

  "getFeatureNames" should "just return the strings in the featureDict, plus a bias feature" in {
    generator.getFeatureNames() should be(Array("bias", "feature1", "feature2", "feature3"))
  }

  "getLocalSubgraph" should "find correct a subgraph on a simple graph" in {
    // We already have a good test of this in BfsPathFinderSpec, so I'm just going to sanity check
    // a few things in here, instead of enumerating the whole subgraph.
    val subgraph = generator.getLocalSubgraph(instance)
    subgraph should contain(Path(1, Array(2), Array(1), Array(false)))
    subgraph should contain(Path(1, Array(4, 7), Array(3, 3), Array(false, true)))
    subgraph should contain(Path(1, Array(2, 3), Array(1, 2), Array(false, false)))
    subgraph should contain(Path(2, Array(3), Array(2), Array(false)))
    subgraph should contain(Path(1, Array(4), Array(3), Array(false)))
    subgraph should contain(Path(1, Array(4, 5), Array(3, 4), Array(false, false)))
  }

  "filterSubgraph" should "filter unallowed paths from the subgraph" in {
    val subgraph = Set(
      Path(1, Array(3), Array(1), Array(false)),
      Path(1, Array(3), Array(1), Array(true)),
      Path(1, Array(2, 3), Array(4, 1), Array(false, false)),
      Path(1, Array(3, 1, 3), Array(2, 1, 2), Array(false, true, false))
    )
    val instance13 = new NodePairInstance(1, 3, true, graph)
    generator.filterSubgraph(instance13, subgraph, "rel1") should be(Set(
      Path(1, Array(2, 3), Array(4, 1), Array(false, false))
    ))
    val instance23 = new NodePairInstance(2, 3, true, graph)
    generator.filterSubgraph(instance23, subgraph, "rel1") should be(Set(
      Path(1, Array(3), Array(1), Array(false)),
      Path(1, Array(3), Array(1), Array(true)),
      Path(1, Array(3, 1, 3), Array(2, 1, 2), Array(false, true, false))
    ))
  }

  "extractFeatures" should "run the feature extractors and return a feature matrix" in {
    val generator =
      new NodePairSubgraphFeatureGenerator(params, relation, metadata, outputter, fileUtil = fakeFileUtil) {
        override def createExtractors(params: JValue) = {
          Seq(new FeatureExtractor[NodePairInstance]() {
            override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
              Seq("feature1", "feature2")
            }
          })
        }
      }
    val subgraph = Set(
      Path(1, Array(2), Array(1), Array(false)),
      Path(1, Array(2), Array(2), Array(false))
    )
    val matrixRow = generator.extractFeatures(instance, subgraph).get
    val expectedMatrixRow = new MatrixRow(instance, Array(0, 1, 2), Array(1.0, 1.0, 1.0))
    matrixRow.instance should be(expectedMatrixRow.instance)
    matrixRow.columns should be(expectedMatrixRow.columns)
    matrixRow.values should be(expectedMatrixRow.values)
  }

  "createExtractors" should "create PraFeatureExtractors correctly" in {
    val params: JValue = ("feature extractors" -> List("PraFeatureExtractor"))
    val generator = generatorWithParams(params)
    generator.featureExtractors(0).getClass should be(classOf[PraFeatureExtractor])
  }

  it should "fail on unrecognized feature extractors" in {
    val params: JValue = ("feature extractors" -> List("non-existant extractor"))
    TestUtil.expectError(classOf[IllegalStateException], "Unrecognized feature extractor", new Function() {
      def call() {
        val generator = generatorWithParams(params)
      }
    })
  }

  "featureToIndex" should "use an identity hash if hashing is not enabled" in {
    generator.featureToIndex("feature1") should be(1)
    generator.featureToIndex("feature2") should be(2)
    generator.featureToIndex("feature3") should be(3)
  }

  it should "correctly hash to the feature size when hashing is enabled" in {
    val params: JValue = ("feature size" -> 10)
    val generator = generatorWithParams(params)
    val hash7 = generator.featureDict.getIndex("hash-7")
    val hash2 = generator.featureDict.getIndex("hash-2")
    val string1 = "a"  // hash code is 97
    generator.featureToIndex(string1) should be(hash7)
    val string2 = " "  // hash code is 32
    generator.featureToIndex(string2) should be(hash2)
    val string3 = "asdfasdf"  // hash code is -802263448
    generator.featureToIndex(string3) should be(hash2)
  }

  "createMatrixRow" should "set feature values to 1 and add a bias feature" in {
    val expected = new MatrixRow(instance, Array(0, 3, 2, 1), Array(1.0, 1.0, 1.0, 1.0))
    val matrixRow = generator.createMatrixRow(instance, Seq(3, 2, 1))
    matrixRow.instance should be(expected.instance)
    matrixRow.columns should be(expected.columns)
    matrixRow.values should be(expected.values)
  }
}
