package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.FakeDatasetFactory
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy
import edu.cmu.ml.rtw.pra.features.MostFrequentPathTypeSelector
import edu.cmu.ml.rtw.pra.features.PathTypePolicy
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
  val matrixDir = "some other matrix dir"
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
    |    "mode": "standard",
    |    "features": {
    |      "path finder": {
    |        "walks per source": 900,
    |        "path finding iterations": 9,
    |        "number of paths to keep": 9000,
    |        "path accept policy": "everything"
    |      },
    |      "path follower": {
    |        "name": "matrix multiplication",
    |        "matrix accept policy": "everything",
    |        "walks per path": 90,
    |        "matrix dir": "$matrixDir",
    |        "max fan out": 90,
    |        "normalize walk probabilities": false
    |      }
    |    },
    |    "learning": {
    |      "l1 weight": 9.05,
    |      "l2 weight": 9,
    |      "binarize features": true
    |    }
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
    ("mode" -> "standard") ~
    ("features" ->
      ("path finder" ->
        ("walks per source" -> 900) ~
        ("path finding iterations" -> 9) ~
        ("number of paths to keep" -> 9000) ~
        ("path accept policy" -> "everything")) ~
      ("path follower" ->
        ("name" -> "matrix multiplication") ~
        ("walks per path" -> 90) ~
        ("normalize walk probabilities" -> false) ~
        ("matrix accept policy" -> "everything") ~
        ("matrix dir" -> matrixDir) ~
        ("max fan out" -> 90))) ~
    ("learning" ->
      ("binarize features" -> true) ~
      ("l1 weight" -> 9.05) ~
      ("l2 weight" -> 9))
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
}
