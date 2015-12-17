package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.config.PraConfigBuilder
import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.mutable

class SubgraphFeatureGeneratorSpec extends FlatSpecLike with Matchers {
  type Subgraph = Map[PathType, Set[(Int, Int)]]

  val params: JValue = ("include bias" -> true)
  val graph = new GraphOnDisk("src/test/resources/")
  val config = new PraConfigBuilder[NodePairInstance]().setOutputBase("/").setNoChecks()
    .setGraph(graph).build()
  val fakeFileUtil = new FakeFileUtil

  val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil)
  generator.featureDict.getIndex("feature1")
  generator.featureDict.getIndex("feature2")
  generator.featureDict.getIndex("feature3")

  def getSubgraph(instance: NodePairInstance) = {
    val pathType1 = new BasicPathTypeFactory().fromString("-1-")
    val pathType2 = new BasicPathTypeFactory().fromString("-2-")
    val nodePairs1 = Set((instance.source, 1))
    val nodePairs2 = Set((instance.target, 2))
    Map(instance -> Map(pathType1 -> nodePairs1, pathType2 -> nodePairs2))
  }

  val instance = new NodePairInstance(1, 2, true, graph)
  val dataset = new Dataset[NodePairInstance](Seq(instance))

  "createTrainingMatrix" should "return extracted features from local subgraphs" in {
    val subgraph = getSubgraph(instance)
    val featureMatrix = new FeatureMatrix(List[MatrixRow]().asJava)
    val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil) {
      override def getLocalSubgraphs(data: Dataset[NodePairInstance]) = {
        if (data != dataset) throw new RuntimeException()
        subgraph
      }
      override def extractFeatures(subgraphs: Map[NodePairInstance, Subgraph]) = {
        if (subgraphs != subgraph) throw new RuntimeException()
        featureMatrix
      }
    }
    generator.createTrainingMatrix(dataset) should be(featureMatrix)
  }

  "createTestMatrix" should "create the same thing as createTrainingMatrix" in {
    val subgraph = getSubgraph(instance)
    val matrixRow = new MatrixRow(instance, Array(0, 1, 2), Array(1.0, 1.0, 1.0))
    val featureMatrix = new FeatureMatrix(List(matrixRow).asJava)
    val nodeDict = new Dictionary()
    nodeDict.getIndex("node1")
    nodeDict.getIndex("node2")
    val out = new Outputter(null, fakeFileUtil)
    val config = new PraConfigBuilder[NodePairInstance]().setOutputMatrices(true)
      .setOutputBase("/").setOutputter(out).setNoChecks().build()
    val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil) {
      override def getLocalSubgraphs(data: Dataset[NodePairInstance]) = {
        if (data != dataset) throw new RuntimeException()
        subgraph
      }
      override def extractFeatures(subgraphs: Map[NodePairInstance, Subgraph]) = {
        if (subgraphs != subgraph) throw new RuntimeException()
        featureMatrix
      }
    }
    generator.hashFeature("feature1")
    generator.hashFeature("feature2")

    fakeFileUtil.onlyAllowExpectedFiles
    generator.createTestMatrix(dataset) should be(featureMatrix)
    fakeFileUtil.expectFilesWritten
  }

  it should "not output the matrix when the output dir is null" in {
    val subgraph = getSubgraph(instance)
    val featureMatrix = new FeatureMatrix(List[MatrixRow]().asJava)
    val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil) {
      override def getLocalSubgraphs(data: Dataset[NodePairInstance]) = {
        if (data != dataset) throw new RuntimeException()
        subgraph
      }
      override def extractFeatures(subgraphs: Map[NodePairInstance, Subgraph]) = {
        if (subgraphs != subgraph) throw new RuntimeException()
        featureMatrix
      }
    }
    generator.createTestMatrix(dataset) should be(featureMatrix)
  }

  "removeZeroWeightFeatures" should "not remove anything" in {
    val weights = Seq(1.0, 2.0, 3.0)
    generator.removeZeroWeightFeatures(weights) should be(weights)
  }

  "getFeatureNames" should "just return the strings in the featureDict, plus a bias feature" in {
    generator.getFeatureNames() should be(Array("bias", "feature1", "feature2", "feature3"))
  }

  "getLocalSubgraphs" should "find correct subgraphs on a simple graph" in {
    // Because this is is a randomized process, we just test for things that should show up pretty
    // much all of the time.  If this test fails occasionally, it might not necessarily mean that
    // something is broken.

    // And we're only checking for one training instance, because that's all there is in the
    // dataset.
    val subgraph = generator.getLocalSubgraphs(dataset)(instance)
    val factory = new BasicPathTypeFactory
    subgraph(factory.fromString("-1-")) should contain((1, 2))
    subgraph(factory.fromString("-3-_3-")) should contain((1, 7))
    subgraph(factory.fromString("-1-2-")) should contain((1, 3))
    subgraph(factory.fromString("-2-")) should contain((2, 3))
    subgraph(factory.fromString("-3-")) should contain((1, 4))
    subgraph(factory.fromString("-3-4-")) should contain((1, 5))
  }

  "extractFeatures" should "run the feature extractors and return a feature matrix" in {
    val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil) {
      override def createExtractors(params: JValue) = {
        Seq(new FeatureExtractor() {
          override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
            Seq("feature1", "feature2")
          }
        })
      }
    }
    val subgraph = getSubgraph(instance)
    val featureMatrix = generator.extractFeatures(subgraph)
    featureMatrix.size should be(1)
    val matrixRow = featureMatrix.getRow(0)
    val expectedMatrixRow = new MatrixRow(instance, Array(0, 1, 2), Array(1.0, 1.0, 1.0))
    matrixRow.instance should be(expectedMatrixRow.instance)
    matrixRow.columns should be(expectedMatrixRow.columns)
    matrixRow.values should be(expectedMatrixRow.values)
  }

  "createExtractors" should "create PraFeatureExtractors correctly" in {
    val params: JValue = ("feature extractors" -> List("PraFeatureExtractor"))
    val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil)
    generator.featureExtractors(0).getClass should be(classOf[PraFeatureExtractor])
  }

  it should "create OneSidedFeatureExtractors correctly" in {
    val params: JValue = ("feature extractors" -> List("OneSidedFeatureExtractor"))
    val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil)
    generator.featureExtractors(0).getClass should be(classOf[OneSidedFeatureExtractor])
  }

  it should "fail on unrecognized feature extractors" in {
    val params: JValue = ("feature extractors" -> List("non-existant extractor"))
    TestUtil.expectError(classOf[IllegalStateException], "Unrecognized feature extractor", new Function() {
      def call() {
        val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil)
      }
    })
  }

  "hashFeature" should "use an identity hash if hashing is not enabled" in {
    generator.hashFeature("feature1") should be(1)
    generator.hashFeature("feature2") should be(2)
    generator.hashFeature("feature3") should be(3)
  }

  it should "correctly hash to the feature size when hashing is enabled" in {
    val params: JValue = ("feature size" -> 10)
    val generator = new SubgraphFeatureGenerator(params, config, fakeFileUtil)
    val hash7 = generator.featureDict.getIndex("hash-7")
    val hash2 = generator.featureDict.getIndex("hash-2")
    val string1 = "a"  // hash code is 97
    generator.hashFeature(string1) should be(hash7)
    val string2 = " "  // hash code is 32
    generator.hashFeature(string2) should be(hash2)
    val string3 = "asdfasdf"  // hash code is -802263448
    generator.hashFeature(string3) should be(hash2)
  }

  "createMatrixRow" should "set feature values to 1 and add a bias feature" in {
    val expected = new MatrixRow(instance, Array(0, 3, 2, 1), Array(1.0, 1.0, 1.0, 1.0))
    val matrixRow = generator.createMatrixRow(instance, Seq(3, 2, 1))
    matrixRow.instance should be(expected.instance)
    matrixRow.columns should be(expected.columns)
    matrixRow.values should be(expected.values)
  }
}
