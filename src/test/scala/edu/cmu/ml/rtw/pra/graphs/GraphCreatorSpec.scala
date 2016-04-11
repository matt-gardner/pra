package edu.cmu.ml.rtw.pra.graphs

import java.io.BufferedReader
import java.io.StringReader

import org.scalatest._

import edu.cmu.ml.rtw.pra.experiments.Outputter
import com.mattg.util.FileUtil
import com.mattg.util.FakeFileUtil
import com.mattg.util.TestUtil
import com.mattg.util.TestUtil.Function

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

class GraphCreatorSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger

  val embeddings1 = "embeddings1"
  val embeddings2 = "embeddings2"

  val concept1 = "c1"
  val concept2 = "c2"
  val string1 = "s1"
  val string2 = "s2"
  val relation = "r"
  val aliasRelation = "@ALIAS@"

  val svoFile = "/svo_file"
  val svoFileContents =
      string1 + "\t" + relation + "\t" + string2 + "\t1\n" +
      string1 + "\t" + relation + "\t" + string2 + "\t1\n"
  val kbFile = "/kb_file"
  val kbFileContents = concept1 + "\t" + concept2 + "\t" + relation + "\n"
  val aliasFile = "/alias_file"
  val aliasFileContents =
      concept1 + "\t" + string1 + "\n" +
      concept2 + "\t" + string2 + "\n"

  val svoRelationSetParams = ("relation file" -> svoFile) ~ ("is kb" -> false)

  val kbRelationSetParams =
    ("relation file" -> kbFile) ~
    ("is kb" -> true) ~
    ("alias file" -> aliasFile) ~
    ("alias file format" -> "nell")

  val nodeDictionaryFile = "/test graph/node_dict.tsv"
  val expectedNodeDictionaryFileContents =
      "1\t" + string1 + "\n" +
      "2\t" + string2 + "\n" +
      "3\t" + concept1 + "\n" +
      "4\t" + concept2 + "\n"

  val edgeDictionaryFile = "/test graph/edge_dict.tsv"
  val expectedEdgeDictionaryFileContents =
      "1\t" + aliasRelation + "\n" +
      "2\t" + relation + "\n"

  val edgesFile = "/test graph/graph_chi/edges.tsv"
  val expectedEdgeFileContents =
      "1\t3\t1\n" +
      "2\t4\t1\n" +
      "1\t2\t2\n" +
      "1\t2\t2\n" +
      "3\t4\t2\n"
  val expectedDedupedEdgeFileContents =
      "1\t3\t1\n" +
      "2\t4\t1\n" +
      "1\t2\t2\n" +
      "3\t4\t2\n"

  val longerEdgesFile = "/test graph/graph_chi/edges.tsv"
  val longerEdgeFileContents =
      "1\t1\t1\n" +
      "2\t2\t2\n" +
      "3\t3\t3\n" +
      "4\t4\t4\n" +
      "5\t5\t5\n"

  val shardsFile = "/test graph/num_shards.tsv"
  val expectedShardsFileContents = "2\n"

  val matrixFile1 = "/test graph/matrices/1"
  val expectedMatrixFile1Contents =
      "Relation 1\n" +
      "1\t3\n" +
      "2\t4\n"

  val matrixFile2 = "/test graph/matrices/2"
  val expectedMatrixFile2Contents =
      "Relation 2\n" +
      "1\t2\n" +
      "1\t2\n" +
      "3\t4\n"

  val matrixFile12 = "/test graph/matrices/1-2"
  val expectedMatrixFile12Contents =
      "Relation 1\n" +
      "1\t3\n" +
      "2\t4\n" +
      "Relation 2\n" +
      "1\t2\n" +
      "1\t2\n" +
      "3\t4\n"

  val defaultRelSetParams =
    ("relation file" -> "test relation file") ~
    ("is kb" -> false)
  val embeddingsParams1: JValue = ("embeddings file" -> embeddings1)
  val embeddingsParams2: JValue = ("embeddings file" -> embeddings2)

  val defaultGraphParams: JValue =
    ("name" -> "test graph") ~
    ("output plain text file" -> true) ~
    ("output binary file" -> false)

  def getFileUtil = {
    val f = new FakeFileUtil()
    f.addFileToBeRead(svoFile, svoFileContents)
    f.addFileToBeRead(kbFile, kbFileContents)
    f.addFileToBeRead(aliasFile, aliasFileContents)
    f
  }

  "createGraphChiRelationGraph" should "make a correct simple graph" in {
    val params = (
      ("relation sets" -> List(svoRelationSetParams, kbRelationSetParams)) ~
      ("create matrices" -> false)
    ) merge defaultGraphParams

    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(nodeDictionaryFile, expectedNodeDictionaryFileContents)
    fileUtil.addExpectedFileWritten(edgeDictionaryFile, expectedEdgeDictionaryFileContents)
    fileUtil.addExpectedFileWritten(edgesFile, expectedEdgeFileContents)

    new GraphCreator("", params, outputter, fileUtil)._runStep()
    fileUtil.expectFilesWritten()
  }

  it should "dedup edges when supposed to" in {
    val fileUtil = getFileUtil
    fileUtil.addExpectedFileWritten(edgesFile, expectedDedupedEdgeFileContents)

    val params = (
      ("relation sets" -> List(svoRelationSetParams, kbRelationSetParams)) ~
      ("deduplicate edges" -> true) ~
      ("create matrices" -> false)
    ) merge defaultGraphParams
    new GraphCreator("", params, outputter, fileUtil)._runStep()
    fileUtil.expectFilesWritten()
  }

  "getSvoPrefixes" should "map relation sets to prefixes" in {
    val relationSets = Seq(
      new RelationSet(defaultRelSetParams merge embeddingsParams1, outputter),
      new RelationSet(defaultRelSetParams merge embeddingsParams1, outputter),
      new RelationSet(defaultRelSetParams merge embeddingsParams2, outputter))

    val prefixes = new GraphCreator("", defaultGraphParams, outputter, getFileUtil).getSvoPrefixes(relationSets)
    prefixes(relationSets(0)) should be("1-")
    prefixes(relationSets(1)) should be("1-")
    prefixes(relationSets(2)) should be("2-")
  }

  it should "give null prefixes when there is no need for them" in {
    val relationSets = Seq(
      new RelationSet(defaultRelSetParams merge embeddingsParams1, outputter),
      new RelationSet(defaultRelSetParams merge embeddingsParams1, outputter))

    // If there's no ambiguity about the embeddings, the prefix map should return null.
    val null_prefixes = new GraphCreator("", defaultGraphParams, outputter, getFileUtil).getSvoPrefixes(relationSets)
    null_prefixes(relationSets(0)) should be(null)
    null_prefixes(relationSets(1)) should be(null)
  }

  // Why test this method?  Well, the primary impetus at the moment is that I am a little OCD
  // about coverage statistics.  But in writing the test I discovered there was a bug in the
  // code due to the long trailing zeros (the initial test was off by an order of magnitude), so
  // there actually is a good reason to test this.  And I can also justify this by saying that
  // any change to the number of shards produced should be well thought out.  I want a change to
  // break a test, so that the person changing it will stop and think a bit about whether the
  // change is good.  And then change the test to reflect the better shard numbers.
  "getNumShards" should "return correct shard numbers" in {
    val creator = new GraphCreator("", defaultGraphParams, outputter, getFileUtil)
    creator.getNumShards(4999999) should be(2)
    creator.getNumShards(5000000) should be(3)
    creator.getNumShards(9999999) should be(3)
    creator.getNumShards(10000000) should be(4)
    creator.getNumShards(39999999) should be(4)
    creator.getNumShards(40000000) should be(5)
    creator.getNumShards(99999999) should be(5)
    creator.getNumShards(100000000) should be(6)
    creator.getNumShards(149999999) should be(6)
    creator.getNumShards(150000000) should be(7)
    creator.getNumShards(249999999) should be(7)
    creator.getNumShards(250000000) should be(8)
    creator.getNumShards(349999999) should be(8)
    creator.getNumShards(450000000) should be(9)
    creator.getNumShards(499999999) should be(9)
    creator.getNumShards(500000000) should be(10)
  }

  "outputMatrices" should "split files with small matrix file size" in {
    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addFileToBeRead(edgesFile, expectedEdgeFileContents)
    fileUtil.addExpectedFileWritten(matrixFile1, expectedMatrixFile1Contents)
    fileUtil.addExpectedFileWritten(matrixFile2, expectedMatrixFile2Contents)

    new GraphCreator("", defaultGraphParams, outputter, fileUtil).createMatrices(edgesFile, 4)
    fileUtil.expectFilesWritten()
  }

  it should "split files with lots of relations" in {
    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addFileToBeRead(edgesFile, longerEdgeFileContents)
    fileUtil.addExpectedFileWritten("/test graph/matrices/1-2", "Relation 1\n1\t1\nRelation 2\n2\t2\n")
    fileUtil.addExpectedFileWritten("/test graph/matrices/3-4", "Relation 3\n3\t3\nRelation 4\n4\t4\n")
    fileUtil.addExpectedFileWritten("/test graph/matrices/5", "Relation 5\n5\t5\n")

    new GraphCreator("", defaultGraphParams, outputter, fileUtil).createMatrices(edgesFile, 2)
    fileUtil.expectFilesWritten()
  }

  it should "make one large file with large matrix file size" in {
    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addFileToBeRead(edgesFile, expectedEdgeFileContents)
    fileUtil.addExpectedFileWritten(matrixFile12, expectedMatrixFile12Contents)

    new GraphCreator("", defaultGraphParams, outputter, fileUtil).createMatrices(edgesFile, 40)
    fileUtil.expectFilesWritten()
  }

  val rel_set_params =
    ("name" -> "generated") ~
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
    ("rule_prob_stddev" -> 0.2)
  val data_file = "synthetic_relation_sets/generated/data.tsv"

  "generateSyntheticRelationSet" should "create a dataset when one doesn't exist" in {
    val instances = Seq((1, "rel", 2))
    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles()
    fileUtil.addExpectedFileWritten(data_file, "1\trel\t2\t1\n")

    val creator = new GraphCreator("", defaultGraphParams, outputter, fileUtil)
    creator.synthetic_data_creator_factory = new FakeSyntheticDataCreatorFactory(instances)
    creator.generateSyntheticRelationSet(rel_set_params)
    fileUtil.expectFilesWritten
  }

  it should "not create a dataset when one already exists" in {
    val fileUtil = getFileUtil
    fileUtil.addExistingFile("synthetic_relation_sets/generated/")
    fileUtil.addFileToBeRead("synthetic_relation_sets/generated/params.json", compact(render(rel_set_params)))
    fileUtil.onlyAllowExpectedFiles()

    val creator = new GraphCreator("", defaultGraphParams, outputter, fileUtil)
    creator.synthetic_data_creator_factory = new FakeSyntheticDataCreatorFactory(Seq())
    creator.generateSyntheticRelationSet(rel_set_params)
    fileUtil.expectFilesWritten
  }

  it should "throw an exception when parameters don't match" in {
    val random_param: JValue = ("num_entities" -> 1000)
    val params = rel_set_params merge random_param
    val fileUtil = getFileUtil
    fileUtil.addExistingFile("synthetic_relation_sets/generated/")
    fileUtil.addFileToBeRead("synthetic_relation_sets/generated/params.json", compact(render(params)))
    val creator = new GraphCreator("", defaultGraphParams, outputter, fileUtil)
    creator.synthetic_data_creator_factory = new FakeSyntheticDataCreatorFactory(Seq())
    TestUtil.expectError(classOf[IllegalStateException], "parameters don't match", new Function() {
      def call() {
        creator.generateSyntheticRelationSet(rel_set_params)
      }
    })
  }
}

class FakeSyntheticDataCreatorFactory(instances: Seq[(Int, String, Int)]) extends ISyntheticDataCreatorFactory {
  def getSyntheticDataCreator(base_dir: String, params: JValue, outputter: Outputter, fileUtil: FileUtil) = {
    new FakeSyntheticDataCreator(base_dir, params, outputter, fileUtil, instances)
  }
}

class FakeSyntheticDataCreator(
    base_dir: String,
    params: JValue,
    outputter: Outputter,
    fileUtil: FileUtil,
    instances: Seq[(Int, String, Int)])
    extends SyntheticDataCreator(base_dir, params, outputter, fileUtil) {

  override def createRelationSet() {
    outputRelationSet(instances)
  }
}
