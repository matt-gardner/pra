package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.pra.experiments.Dataset
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

  val relationEmbeddingsFilename = "/relation/embeddings"
  val relationEmbeddingsFile = "relation1\t0.1\t0.2\n"

  val baseSpecFilename = "/base/spec/file"
  val baseSpecFile = """{
    |  "kb files": "nell kb files",
    |  "graph files": "nell graph files",
    |  "split": "nell split",
    |  "l1 weight": 9.05,
    |  "l2 weight": 9,
    |  "walks per source": 900,
    |  "walks per path": 90,
    |  "path finding iterations": 9,
    |  "number of paths to keep": 9000,
    |  "matrix accept policy": "everything",
    |  "path accept policy": "everything",
    |  "max matrix feature fan out": 90,
    |  "normalize walk probabilities": false,
    |  "binarize features": true,
    |  "path follower": "matrix multiplication",
    |}""".stripMargin

  val baseParams: JValue =
    ("kb files" -> "nell kb files") ~
    ("graph files" -> "nell graph files") ~
    ("split" -> "nell split") ~
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

  val pathTypeFactoryParams: JValue =
    ("path type factory" ->
      ("name" -> "VectorPathTypeFactory") ~
      ("spikiness" -> .2) ~
      ("reset weight" -> .8) ~
      ("embeddings" -> List(relationEmbeddingsFilename)))

  val fileUtil: FakeFileUtil = {
    val f = new FakeFileUtil
    f.addFileToBeRead(baseSpecFilename, baseSpecFile)
    f.addFileToBeRead(extendedSpecFilename, extendedSpecFile)
    f.addFileToBeRead(overwrittenSpecFilename, overwrittenSpecFile)
    f.addFileToBeRead(nestedSpecFilename, nestedSpecFile)
    f.addFileToBeRead(relationEmbeddingsFilename, relationEmbeddingsFile)
    f.addFileToBeRead("nell graph files/num_shards.tsv", "1\n")
    f
  }

  "readSpecFile" should "read a simple map" in {
    new SpecFileReader(fileUtil).readSpecFile(baseSpecFilename) should be(baseParams)
  }

  it should "read a file with an extension" in {
    new SpecFileReader(fileUtil).readSpecFile(extendedSpecFilename) should be(extendedParams)
  }

  it should "overwrite parameters and do nested loads" in {
    new SpecFileReader(fileUtil).readSpecFile(overwrittenSpecFilename) should be(overwrittenParams)
  }

  it should "read nested parameters" in {
    new SpecFileReader(fileUtil).readSpecFile(nestedSpecFilename) should be(nestedParams)
    println(nestedParams \ "level 5")
  }

  "setPraConfigFromParams" should "initialize simple params" in {
    val builder = new PraConfig.Builder
    builder.noChecks()
    new SpecFileReader(fileUtil).setPraConfigFromParams(baseParams, builder)
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
  }

  it should "give default values with empty params" in {
    val builder = new PraConfig.Builder
    builder.noChecks()
    new SpecFileReader(fileUtil).setPraConfigFromParams(new JObject(Nil), builder)
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
    new SpecFileReader(fileUtil).setPraConfigFromParams(pathTypeFactoryParams, builder)
    val config = builder.build()
    val factory = config.pathTypeFactory.asInstanceOf[VectorPathTypeFactory]
    factory.getResetWeight should be(Math.exp(0.2 * 0.8))
    factory.getSpikiness should be(0.2)
    factory.getEmbeddingsMap.size should be(1)
    factory.getEmbeddingsMap.get(1) should be(new Vector(Array(0.1, 0.2)))
    val badParams: JValue = ("path type factory" -> ("name" -> "nope"))
    TestUtil.expectError(classOf[RuntimeException], new Function() {
      def call() {
        new SpecFileReader(fileUtil).setPraConfigFromParams(badParams, builder)
      }
    })
  }

  it should "handle path follower factories" in {
    val builder = new PraConfig.Builder
    builder.noChecks()
    var params: JValue = ("path follower" -> "random walks")
    new SpecFileReader(fileUtil).setPraConfigFromParams(params, builder)
    val config = builder.build()
    config.pathFollowerFactory.getClass should be(classOf[RandomWalkPathFollowerFactory])
    params = ("path follower" -> "bad")
    TestUtil.expectError(classOf[RuntimeException], new Function() {
      def call() {
        new SpecFileReader(fileUtil).setPraConfigFromParams(params, builder)
      }
    })
  }

  it should "handle path type selectors" in {
    val builder = new PraConfig.Builder
    builder.noChecks().setFileUtil(fileUtil)
    val params: JValue = ("path type selector" ->
      ("name" -> "VectorClusteringPathTypeSelector") ~ ("similarity threshold" -> .3))
    new SpecFileReader(fileUtil).setPraConfigFromParams(pathTypeFactoryParams merge params, builder)
    val config = builder.build()
    val selector = config.pathTypeSelector.asInstanceOf[VectorClusteringPathTypeSelector]
    selector.getSimilarityThreshold should be(.3)
    val badParams: JValue = ("path type selector" -> ("name" -> "nope"))
    TestUtil.expectError(classOf[RuntimeException], new Function() {
      def call() {
        new SpecFileReader(fileUtil).setPraConfigFromParams(badParams, builder)
      }
    })
  }
}
