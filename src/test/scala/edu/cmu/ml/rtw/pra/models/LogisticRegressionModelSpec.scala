package edu.cmu.ml.rtw.pra.models

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.experiments.RelationMetadata
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.features.NodePairSubgraphFeatureGenerator
import edu.cmu.ml.rtw.pra.features.Subgraph
import edu.cmu.ml.rtw.pra.features.extractors.FeatureExtractor
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory

import com.mattg.util.FakeFileUtil
import com.mattg.util.MutableConcurrentDictionary

import org.scalatest._
import org.json4s._

class LogisticRegressionModelSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger
  val metadata = RelationMetadata.empty
  val nodeDict = new MutableConcurrentDictionary
  val graph = new GraphInMemory(Array(), nodeDict, nodeDict)

  "loadFromFile" should "load feature weights and update a feature dictionary" in {
    // This test is somewhat involved, because I want to be sure the integration with using the
    // loaded feature dictionary with the feature generator actually works.
    val fileUtil = new FakeFileUtil
    val dictionary = new MutableConcurrentDictionary
    val modelFile = "/modelFile.tsv"
    val modelFileContents = "feature 1\t0.0\nfeature 2\t-1.0\nfeature 3\t2.0"
    fileUtil.addFileToBeRead(modelFile, modelFileContents)
    val model = LogisticRegressionModel.loadFromFile(modelFile, dictionary, outputter, fileUtil)

    dictionary.hasKey("feature 1") should be(false)
    model.lrWeights.size should be(3)  // index 0 is unused
    model.lrWeights(dictionary.getIndex("feature 2")) should be(-1.0)
    model.lrWeights(dictionary.getIndex("feature 3")) should be(2.0)

    val generator = new NodePairSubgraphFeatureGenerator(
      JNothing,
      "relation",
      metadata,
      outputter,
      dictionary,
      fileUtil
    ) {
      override def createExtractors(params: JValue) = {
        Seq(new FeatureExtractor[NodePairInstance] {
          override def extractFeatures(instance: NodePairInstance, subgraph: Subgraph) = {
            Seq("feature 3", "feature 1", "feature 2", "feature 1", "feature 4")
          }
        })
      }
    }
    val row = generator.constructMatrixRow(new NodePairInstance(1, 1, true, graph))
    model.classifyMatrixRow(row.get) should be(1.0 +- 0.001)
  }
}
