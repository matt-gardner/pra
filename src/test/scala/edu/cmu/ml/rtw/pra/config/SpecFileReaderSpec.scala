package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.FakeDatasetFactory
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.MatrixPathFollowerFactory
import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy
import edu.cmu.ml.rtw.pra.features.MostFrequentPathTypeSelector
import edu.cmu.ml.rtw.pra.features.PathTypePolicy
import edu.cmu.ml.rtw.pra.features.RandomWalkPathFollowerFactory
import edu.cmu.ml.rtw.pra.features.SingleEdgeExcluderFactory
import edu.cmu.ml.rtw.pra.features.VectorClusteringPathTypeSelector
import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function
import edu.cmu.ml.rtw.users.matt.util.Vector

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

class SpecFileReaderSpec extends FlatSpecLike with Matchers {

  val nodeDictionaryFile = "1\tnode\n2\tnode 2\n"
  val edgeDictionaryFile = "1\tedge\n2\tedge 2\n"

  val relationEmbeddingsFilename = "/relation/embeddings"
  val relationEmbeddingsFile = "edge\t0.1\t0.2\n"

  val graphDir = "nell graph files"
  val relationMetadataFilename = "nell relation metadata"
  val splitName = "nell split"
  val relationSetFilename = "relation set filename"
  val generatedDataName = "generated data name"

  val baseSpecFilename = "/base/spec/file"
  val baseSpecFile = s"""{
    |  "graph": {
    |    "name": "$graphDir",
    |    "relation sets": [
    |      "$relationSetFilename",
    |      {
    |        "type": "generated",
    |        "generation params": {
    |          "name": "$generatedDataName",
    |          "num_entities": 1,
    |          "num_base_relations": 2,
    |          "num_base_relation_training_duplicates": 3,
    |          "num_base_relation_testing_duplicates": 4,
    |          "num_base_relation_overlapping_instances": 5,
    |          "num_base_relation_noise_instances": 6,
    |          "num_pra_relations": 7,
    |          "num_pra_relation_training_instances": 8,
    |          "num_pra_relation_testing_instances": 9,
    |          "num_rules": 10,
    |          "min_rule_length": 11,
    |          "max_rule_length": 12,
    |          "rule_prob_mean": 0.6,
    |          "rule_prob_stddev": 0.2,
    |          "num_noise_relations": 13,
    |          "num_noise_relation_instances": 14
    |        }
    |      }
    |    ],
    |    "deduplicate edges": true
    |  },
    |  "relation metadata": "$relationMetadataFilename",
    |  "split": "$splitName",
    |  "pra parameters": {
    |    "pra mode": "standard",
    |    "l1 weight": 9.05,
    |    "l2 weight": 9,
    |    "walks per source": 900,
    |    "walks per path": 90,
    |    "path finding iterations": 9,
    |    "number of paths to keep": 9000,
    |    "matrix accept policy": "everything",
    |    "path accept policy": "everything",
    |    "max matrix feature fan out": 90,
    |    "normalize walk probabilities": false,
    |    "binarize features": true,
    |    "path follower": "matrix multiplication",
    |  }
    |}""".stripMargin

  val generatedRelationSetSpec: JValue =
    ("type" -> "generated") ~
    ("generation params" ->
      ("name" -> generatedDataName) ~
      ("num_entities" -> 1) ~
      ("num_base_relations" -> 2) ~
      ("num_base_relation_training_duplicates" -> 3) ~
      ("num_base_relation_testing_duplicates" -> 4) ~
      ("num_base_relation_overlapping_instances" -> 5) ~
      ("num_base_relation_noise_instances" -> 6) ~
      ("num_pra_relations" -> 7) ~
      ("num_pra_relation_training_instances" -> 8) ~
      ("num_pra_relation_testing_instances" -> 9) ~
      ("num_rules" -> 10) ~
      ("min_rule_length" -> 11) ~
      ("max_rule_length" -> 12) ~
      ("num_noise_relations" -> 13) ~
      ("num_noise_relation_instances" -> 14) ~
      ("rule_prob_mean" -> 0.6) ~
      ("rule_prob_stddev" -> 0.2))

  val graphSpec: JValue =
    ("name" -> graphDir) ~
    ("relation sets" -> List(JString(relationSetFilename), generatedRelationSetSpec)) ~
    ("deduplicate edges" -> true)
  val graphParamsName = "graph_params"
  val graphSpecFilename = s"param_files/${graphParamsName}.json"
  val graphSpecFile = pretty(render(graphSpec))

  val praSpec: JValue =
    ("pra mode" -> "standard") ~
    ("l1 weight" -> 9.05) ~
    ("l2 weight" -> 9) ~
    ("walks per source" -> 900) ~
    ("walks per path" -> 90) ~
    ("path finding iterations" -> 9) ~
    ("number of paths to keep" -> 9000) ~
    ("max matrix feature fan out" -> 90) ~
    ("normalize walk probabilities" -> false) ~
    ("binarize features" -> true) ~
    ("matrix accept policy" -> "everything") ~
    ("path follower" -> "matrix multiplication") ~
    ("path accept policy" -> "everything")
  val praSpecFilename = "/pra/params/spec/file"
  val praSpecFile = pretty(render(praSpec))

  val baseParams: JValue =
    ("relation metadata" -> relationMetadataFilename) ~
    ("graph" -> graphSpec) ~
    ("split" -> splitName) ~
    ("pra parameters" -> praSpec)

  val justGraphSpec: JValue = ("graph" -> graphSpec)

  val extendedSpecFilename = "/extended/spec/file"
  val extendedSpecFile = """load /base/spec/file
    |{
    |  "additional param": "additional kb files"
    |}""".stripMargin

  val extendedParams: JValue = {
    val extraParam: JValue = ("additional param" -> "additional kb files")
    baseParams merge extraParam
  }

  val overwrittenSpecFilename = "/overwritten/spec/file"
  val overwrittenSpecFile = """load /extended/spec/file
    |{
    |  "additional param": "overwritten kb files"
    |}""".stripMargin

  val overwrittenParams: JValue = {
    val extraParam: JValue = ("additional param" -> "overwritten kb files")
    baseParams merge extraParam
  }

  val nestedSpecFilename = "/nested/spec/file"
  val nestedSpecFile = """{
    |  "level 1": {
    |    "level 2": {
    |      "level 3": {
    |        "level 4": "finished"
    |      }
    |    }
    |  }
    |}""".stripMargin

  val nestedParams: JValue =
    ("level 1" -> ("level 2" -> ("level 3" -> ("level 4" -> "finished"))))

  val nestedLoadSpecFilename = "/nested_load/spec/file"
  val nestedLoadSpecFile = s"""{
    |  "pra parameters": "load $praSpecFilename",
    |  "graph": "load $graphParamsName",
    |  "just for good measure": {
    |    "pra parameters": "load $praSpecFilename"
    |  }
    |  "and a list": ["load $praSpecFilename"]
    |}""".stripMargin

  val nestedLoadParams: JValue =
    ("pra parameters" -> praSpec) ~
    ("graph" -> graphSpec) ~
    ("just for good measure" -> ("pra parameters" -> praSpec)) ~
    ("and a list" -> List(praSpec))

  val pathTypeFactoryParams: JValue =
    ("pra parameters" -> ("path type factory" ->
      ("name" -> "VectorPathTypeFactory") ~
      ("spikiness" -> .2) ~
      ("reset weight" -> .8) ~
      ("matrix dir" -> "matrix dir") ~
      ("embeddings" -> List(relationEmbeddingsFilename))))

  val fileUtil: FakeFileUtil = {
    val f = new FakeFileUtil
    f.addFileToBeRead("/graphs/" + graphDir + "/node_dict.tsv", nodeDictionaryFile)
    f.addFileToBeRead("/graphs/" + graphDir + "/edge_dict.tsv", edgeDictionaryFile)
    f.addFileToBeRead("/graphs/" + graphDir + "/num_shards.tsv", "1\n")
    f.addFileToBeRead(baseSpecFilename, baseSpecFile)
    f.addFileToBeRead(extendedSpecFilename, extendedSpecFile)
    f.addFileToBeRead(overwrittenSpecFilename, overwrittenSpecFile)
    f.addFileToBeRead(nestedSpecFilename, nestedSpecFile)
    f.addFileToBeRead(relationEmbeddingsFilename, relationEmbeddingsFile)
    f.addFileToBeRead(praSpecFilename, praSpecFile)
    f.addFileToBeRead(graphSpecFilename, graphSpecFile)
    f.addFileToBeRead(nestedLoadSpecFilename, nestedLoadSpecFile)
    f
  }

  "readSpecFile" should "read a simple map" in {
    val params = new SpecFileReader("", fileUtil).readSpecFile(baseSpecFilename)
    params \ "graph" should be(graphSpec)
    params \ "pra parameters" should be(praSpec)
    params should be(baseParams)
  }

  it should "read a file with an extension" in {
    new SpecFileReader("", fileUtil).readSpecFile(extendedSpecFilename) should be(extendedParams)
  }

  it should "overwrite parameters and do nested loads" in {
    new SpecFileReader("", fileUtil).readSpecFile(overwrittenSpecFilename) should be(overwrittenParams)
  }

  it should "read nested parameters" in {
    new SpecFileReader("", fileUtil).readSpecFile(nestedSpecFilename) should be(nestedParams)
  }

  it should "expand loads found in values in the json" in {
    new SpecFileReader("", fileUtil).readSpecFile(nestedLoadSpecFilename) should be(nestedLoadParams)
  }

  "setPraConfigFromParams" should "initialize simple params" in {
    val builder = new PraConfig.Builder
    builder.noChecks()
    new SpecFileReader("", fileUtil).setPraConfigFromParams(baseParams, builder)
    val config = builder.build()
    config.numIters should be(9)
    config.walksPerSource should be(900)
    config.numPaths should be(9000)
    config.pathTypePolicy should be(PathTypePolicy.EVERYTHING)
    config.maxMatrixFeatureFanOut should be(90)
    config.walksPerPath should be(90)
    config.acceptPolicy should be(MatrixRowPolicy.EVERYTHING)
    config.normalizeWalkProbabilities should be(false)
    config.l2Weight should be(9)
    config.l1Weight should be(9.05)
    config.binarizeFeatures should be(true)
    config.pathFollowerFactory.getClass should be(classOf[MatrixPathFollowerFactory])
    config.nodeDict.getIndex("node 2") should be(2);
    config.edgeDict.getIndex("edge 2") should be(2);
  }

  it should "give default values with empty params" in {
    val builder = new PraConfig.Builder
    builder.noChecks()
    new SpecFileReader("", fileUtil).setPraConfigFromParams(justGraphSpec, builder)
    val config = builder.build()
    config.numIters should be(3)
    config.walksPerSource should be(200)
    config.numPaths should be(500)
    config.pathTypePolicy should be(PathTypePolicy.PAIRED_ONLY)
    config.maxMatrixFeatureFanOut should be(100)
    config.walksPerPath should be(50)
    config.acceptPolicy should be(MatrixRowPolicy.ALL_TARGETS)
    config.normalizeWalkProbabilities should be(true)
    config.l2Weight should be(1)
    config.l1Weight should be(0.05)
    config.binarizeFeatures should be(false)
    config.pathFollowerFactory.getClass should be(classOf[RandomWalkPathFollowerFactory])
    config.pathTypeFactory.getClass should be(classOf[BasicPathTypeFactory])
    config.pathTypeSelector.getClass should be(classOf[MostFrequentPathTypeSelector])
    config.edgeExcluderFactory.getClass should be(classOf[SingleEdgeExcluderFactory])
  }

  it should "handle path type factories" in {
    val builder = new PraConfig.Builder
    builder.noChecks().setFileUtil(fileUtil)
    new SpecFileReader("", fileUtil).setPraConfigFromParams(
      pathTypeFactoryParams merge justGraphSpec, builder)
    val config = builder.build()
    config.matrixDir should be("matrix dir")
    val factory = config.pathTypeFactory.asInstanceOf[VectorPathTypeFactory]
    factory.getResetWeight should be(Math.exp(0.2 * 0.8))
    factory.getSpikiness should be(0.2)
    factory.getEmbeddingsMap.size should be(1)
    println(factory.getEmbeddingsMap)
    factory.getEmbeddingsMap.get(1) should be(new Vector(Array(0.1, 0.2)))
    val badParams: JValue = ("pra parameters" -> ("path type factory" -> ("name" -> "nope")))
    TestUtil.expectError(classOf[RuntimeException], "Unrecognized path type factory", new Function() {
      def call() {
        new SpecFileReader("", fileUtil).setPraConfigFromParams(badParams merge justGraphSpec, builder)
      }
    })
  }

  it should "disallow bad combinations of path types / path followers" in {
    val builder = new PraConfig.Builder
    builder.setFileUtil(fileUtil)
    val params = (pathTypeFactoryParams removeField { _ == JField("matrix dir", JString("matrix dir")) }
      merge baseParams)
    new SpecFileReader("", fileUtil).setPraConfigFromParams(params, builder)
    builder.setTrainingData(new FakeDatasetFactory().fromReader(null, null));
    TestUtil.expectError(classOf[IllegalStateException], "must specify matrixDir", new Function() {
      def call() {
        builder.build()
      }
    })
  }

  it should "handle path follower factories" in {
    val builder = new PraConfig.Builder
    builder.noChecks()
    var params: JValue = ("pra parameters" -> ("path follower" -> "random walks"))
    new SpecFileReader("", fileUtil).setPraConfigFromParams(params merge justGraphSpec, builder)
    val config = builder.build()
    config.pathFollowerFactory.getClass should be(classOf[RandomWalkPathFollowerFactory])
    params = ("pra parameters" -> ("path follower" -> "bad"))
    TestUtil.expectError(classOf[RuntimeException], "Unrecognized path follower", new Function() {
      def call() {
        new SpecFileReader("", fileUtil).setPraConfigFromParams(params merge justGraphSpec, builder)
      }
    })
  }

  it should "handle path type selectors" in {
    val builder = new PraConfig.Builder
    builder.noChecks().setFileUtil(fileUtil)
    val params: JValue = ("pra parameters" -> ("path type selector" ->
      ("name" -> "VectorClusteringPathTypeSelector") ~ ("similarity threshold" -> .3)))
    new SpecFileReader("", fileUtil).setPraConfigFromParams(
      pathTypeFactoryParams merge params merge justGraphSpec, builder)
    val config = builder.build()
    val selector = config.pathTypeSelector.asInstanceOf[VectorClusteringPathTypeSelector]
    selector.getSimilarityThreshold should be(.3)
    val badParams: JValue = ("pra parameters" -> ("path type selector" -> ("name" -> "nope")))
    TestUtil.expectError(classOf[RuntimeException], "Unrecognized path type selector", new Function() {
      def call() {
        new SpecFileReader("", fileUtil).setPraConfigFromParams(badParams merge justGraphSpec, builder)
      }
    })
  }
}
