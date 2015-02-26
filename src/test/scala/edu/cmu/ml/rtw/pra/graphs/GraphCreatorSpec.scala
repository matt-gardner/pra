package edu.cmu.ml.rtw.pra.graphs

import java.io.BufferedReader
import java.io.StringReader

import org.scalatest._

import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

class GraphCreatorSpec extends FlatSpecLike with Matchers {
  val embeddings1 = "embeddings1";
  val embeddings2 = "embeddings2";

  val concept1 = "c1";
  val concept2 = "c2";
  val string1 = "s1";
  val string2 = "s2";
  val relation = "r";
  val aliasRelation = "@ALIAS@";

  val svoFile = "/svo_file";
  val svoFileContents =
      string1 + "\t" + relation + "\t" + string2 + "\t1\n" +
      string1 + "\t" + relation + "\t" + string2 + "\t1\n";
  val kbFile = "/kb_file";
  val kbFileContents = concept1 + "\t" + concept2 + "\t" + relation + "\n";
  val aliasFile = "/alias_file";
  val aliasFileContents =
      concept1 + "\t" + string1 + "\n" +
      concept2 + "\t" + string2 + "\n";

  val svoRelationSetFile = "/svo_relation_set";
  val svoRelationSetFileContents =
      "relation file\t" + svoFile + "\n" +
      "is kb\tfalse\n";

  val kbRelationSetFile = "/kb_relation_set";
  val kbRelationSetFileContents =
      "relation file\t" + kbFile + "\n" +
      "is kb\ttrue\n" +
      "alias file\t" + aliasFile + "\n" +
      "alias file format\tnell\n";

  val nodeDictionaryFile = "/node_dict.tsv";
  val expectedNodeDictionaryFileContents =
      "1\t" + string1 + "\n" +
      "2\t" + string2 + "\n" +
      "3\t" + concept1 + "\n" +
      "4\t" + concept2 + "\n";

  val edgeDictionaryFile = "/edge_dict.tsv";
  val expectedEdgeDictionaryFileContents =
      "1\t" + aliasRelation + "\n" +
      "2\t" + relation + "\n";

  val edgesFile = "/graph_chi/edges.tsv";
  val expectedEdgeFileContents =
      "1\t3\t1\n" +
      "2\t4\t1\n" +
      "1\t2\t2\n" +
      "1\t2\t2\n" +
      "3\t4\t2\n";
  val expectedDedupedEdgeFileContents =
      "1\t3\t1\n" +
      "2\t4\t1\n" +
      "1\t2\t2\n" +
      "3\t4\t2\n";

  val longerEdgesFile = "/graph_chi/edges.tsv";
  val longerEdgeFileContents =
      "1\t1\t1\n" +
      "2\t2\t2\n" +
      "3\t3\t3\n" +
      "4\t4\t4\n" +
      "5\t5\t5\n";

  val shardsFile = "/num_shards.tsv";
  val expectedShardsFileContents = "2\n";

  val matrixFile1 = "/matrices/1";
  val expectedMatrixFile1Contents =
      "Relation 1\n" +
      "1\t3\n" +
      "2\t4\n";

  val matrixFile2 = "/matrices/2";
  val expectedMatrixFile2Contents =
      "Relation 2\n" +
      "1\t2\n" +
      "1\t2\n" +
      "3\t4\n";

  val matrixFile12 = "/matrices/1-2";
  val expectedMatrixFile12Contents =
      "Relation 1\n" +
      "1\t3\n" +
      "2\t4\n" +
      "Relation 2\n" +
      "1\t2\n" +
      "1\t2\n" +
      "3\t4\n";

  def getFileUtil = {
    val f = new FakeFileUtil();
    f.addFileToBeRead(svoFile, svoFileContents);
    f.addFileToBeRead(kbFile, kbFileContents);
    f.addFileToBeRead(aliasFile, aliasFileContents);
    f.addFileToBeRead(svoRelationSetFile, svoRelationSetFileContents);
    f.addFileToBeRead(kbRelationSetFile, kbRelationSetFileContents);
    f
  }

  "createGraphChiRelationGraph" should "make a correct simple graph" in {
    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(nodeDictionaryFile, expectedNodeDictionaryFileContents);
    fileUtil.addExpectedFileWritten(edgeDictionaryFile, expectedEdgeDictionaryFileContents);
    fileUtil.addExpectedFileWritten(edgesFile, expectedEdgeFileContents);
    fileUtil.addExpectedFileWritten(shardsFile, expectedShardsFileContents);

    val params =
      ("relation sets" -> List(svoRelationSetFile, kbRelationSetFile)) ~
      ("create matrices" -> false)
    new GraphCreator("/", fileUtil).createGraphChiRelationGraph(params, false)
    fileUtil.expectFilesWritten();
  }

  it should "dedup edges when supposed to" in {
    val fileUtil = getFileUtil
    fileUtil.addExpectedFileWritten(edgesFile, expectedDedupedEdgeFileContents);

    val params =
      ("relation sets" -> List(svoRelationSetFile, kbRelationSetFile)) ~
      ("deduplicate edges" -> true) ~
      ("create matrices" -> false)
    new GraphCreator("/", fileUtil).createGraphChiRelationGraph(params, false)
    fileUtil.expectFilesWritten();
  }

  "getSvoPrefixes" should "map relation sets to prefixes" in {
    val relationSets = Seq(
      RelationSet.fromReader(new BufferedReader(new StringReader(
        "embeddings file\t" + embeddings1 + "\n"))),
      RelationSet.fromReader(new BufferedReader(new StringReader(
        "embeddings file\t" + embeddings1 + "\n"))),
      RelationSet.fromReader(new BufferedReader(new StringReader(
        "embeddings file\t" + embeddings2 + "\n"))))

    val prefixes = new GraphCreator("/").getSvoPrefixes(relationSets);
    prefixes(relationSets(0)) should be("1-")
    prefixes(relationSets(1)) should be("1-")
    prefixes(relationSets(2)) should be("2-")
  }

  it should "give null prefixes when there is no need for them" in {
    val relationSets = Seq(
      RelationSet.fromReader(new BufferedReader(new StringReader(
        "embeddings file\t" + embeddings1 + "\n"))),
      RelationSet.fromReader(new BufferedReader(new StringReader(
        "embeddings file\t" + embeddings1 + "\n"))))

    // If there's no ambiguity about the embeddings, the prefix map should return null.
    val null_prefixes = new GraphCreator("/").getSvoPrefixes(relationSets);
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
    val creator = new GraphCreator("/")
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
    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addFileToBeRead(edgesFile, expectedEdgeFileContents);
    fileUtil.addExpectedFileWritten(matrixFile1, expectedMatrixFile1Contents);
    fileUtil.addExpectedFileWritten(matrixFile2, expectedMatrixFile2Contents);

    new GraphCreator("/", fileUtil).outputMatrices(edgesFile, 4)
    fileUtil.expectFilesWritten();
  }

  it should "split files with lots of relations" in {
    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addFileToBeRead(edgesFile, longerEdgeFileContents);
    fileUtil.addExpectedFileWritten("/matrices/1-2", "Relation 1\n1\t1\nRelation 2\n2\t2\n");
    fileUtil.addExpectedFileWritten("/matrices/3-4", "Relation 3\n3\t3\nRelation 4\n4\t4\n");
    fileUtil.addExpectedFileWritten("/matrices/5", "Relation 5\n5\t5\n");

    new GraphCreator("/", fileUtil).outputMatrices(edgesFile, 2)
    fileUtil.expectFilesWritten();
  }

  it should "make one large file with large matrix file size" in {
    val fileUtil = getFileUtil
    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addFileToBeRead(edgesFile, expectedEdgeFileContents);
    fileUtil.addExpectedFileWritten(matrixFile12, expectedMatrixFile12Contents);

    new GraphCreator("/", fileUtil).outputMatrices(edgesFile, 40)
    fileUtil.expectFilesWritten();
  }
}

