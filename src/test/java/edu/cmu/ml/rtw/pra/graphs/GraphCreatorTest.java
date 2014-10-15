package edu.cmu.ml.rtw.pra.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Lists;

import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil;

public class GraphCreatorTest extends TestCase {
  private FakeFileUtil fileUtil;
  private String embeddings1 = "embeddings1";
  private String embeddings2 = "embeddings2";

  private String concept1 = "c1";
  private String concept2 = "c2";
  private String string1 = "s1";
  private String string2 = "s2";
  private String relation = "r";
  private String aliasRelation = "@ALIAS@";

  private String svoFile = "/svo_file";
  private String svoFileContents =
      string1 + "\t" + relation + "\t" + string2 + "\t1\n" +
      string1 + "\t" + relation + "\t" + string2 + "\t1\n";
  private String kbFile = "/kb_file";
  private String kbFileContents = concept1 + "\t" + concept2 + "\t" + relation + "\n";
  private String aliasFile = "/alias_file";
  private String aliasFileContents =
      concept1 + "\t" + string1 + "\n" +
      concept2 + "\t" + string2 + "\n";

  private String svoRelationSetFile = "/svo_relation_set";
  private String svoRelationSetFileContents =
      "relation file\t" + svoFile + "\n" +
      "is kb\tfalse\n";

  private String kbRelationSetFile = "/kb_relation_set";
  private String kbRelationSetFileContents =
      "relation file\t" + kbFile + "\n" +
      "is kb\ttrue\n" +
      "alias file\t" + aliasFile + "\n" +
      "alias file format\tnell\n";

  private String nodeDictionaryFile = "/node_dict.tsv";
  private String expectedNodeDictionaryFileContents =
      "1\t" + string1 + "\n" +
      "2\t" + string2 + "\n" +
      "3\t" + concept1 + "\n" +
      "4\t" + concept2 + "\n";

  private String edgeDictionaryFile = "/edge_dict.tsv";
  private String expectedEdgeDictionaryFileContents =
      "1\t" + aliasRelation + "\n" +
      "2\t" + relation + "\n";

  private String edgesFile = "/graph_chi/edges.tsv";
  private String expectedEdgeFileContents =
      "1\t3\t1\n" +
      "2\t4\t1\n" +
      "1\t2\t2\n" +
      "1\t2\t2\n" +
      "3\t4\t2\n";
  private String expectedDedupedEdgeFileContents =
      "1\t3\t1\n" +
      "2\t4\t1\n" +
      "1\t2\t2\n" +
      "3\t4\t2\n";


  private String shardsFile = "/num_shards.tsv";
  private String expectedShardsFileContents = "2\n";

  @Override
  public void setUp() {
    fileUtil = new FakeFileUtil();
    fileUtil.addFileToBeRead(svoFile, svoFileContents);
    fileUtil.addFileToBeRead(kbFile, kbFileContents);
    fileUtil.addFileToBeRead(aliasFile, aliasFileContents);
    fileUtil.addFileToBeRead(svoRelationSetFile, svoRelationSetFileContents);
    fileUtil.addFileToBeRead(kbRelationSetFile, kbRelationSetFileContents);
  }

  public void testCreateGraphChiRelationGraphMakesACorrectSimpleGraph() throws IOException {
    fileUtil.onlyAllowExpectedFiles();
    fileUtil.addExpectedFileWritten(nodeDictionaryFile, expectedNodeDictionaryFileContents);
    fileUtil.addExpectedFileWritten(edgeDictionaryFile, expectedEdgeDictionaryFileContents);
    fileUtil.addExpectedFileWritten(edgesFile, expectedEdgeFileContents);
    fileUtil.addExpectedFileWritten(shardsFile, expectedShardsFileContents);

    List<RelationSet> relationSets = Lists.newArrayList();
    relationSets.add(RelationSet.fromFile(svoRelationSetFile, fileUtil));
    relationSets.add(RelationSet.fromFile(kbRelationSetFile, fileUtil));
    GraphCreator creator = new GraphCreator(relationSets, "", false, fileUtil);
    creator.createGraphChiRelationGraph(false);
    fileUtil.expectFilesWritten();
  }

  public void testCreateGraphChiRelationGraphDedupesEdgesWhenSupposedTo() throws IOException {
    fileUtil.addExpectedFileWritten(edgesFile, expectedDedupedEdgeFileContents);

    List<RelationSet> relationSets = Lists.newArrayList();
    relationSets.add(RelationSet.fromFile(svoRelationSetFile, fileUtil));
    relationSets.add(RelationSet.fromFile(kbRelationSetFile, fileUtil));
    GraphCreator creator = new GraphCreator(relationSets, "", true, fileUtil);
    creator.createGraphChiRelationGraph(false);
    fileUtil.expectFilesWritten();
  }

  public void testGetSvoPrefixes() throws IOException {
    List<RelationSet> relationSets = Lists.newArrayList();
    String file = "embeddings file\t" + embeddings1 + "\n";
    relationSets.add(RelationSet.fromReader(new BufferedReader(new StringReader(file))));
    file = "embeddings file\t" + embeddings1 + "\n";
    relationSets.add(RelationSet.fromReader(new BufferedReader(new StringReader(file))));
    file = "embeddings file\t" + embeddings2 + "\n";
    relationSets.add(RelationSet.fromReader(new BufferedReader(new StringReader(file))));

    Map<RelationSet, String> prefixes = new GraphCreator(relationSets, "").getSvoPrefixes();
    assertEquals("1-", prefixes.get(relationSets.get(0)));
    assertEquals("1-", prefixes.get(relationSets.get(1)));
    assertEquals("2-", prefixes.get(relationSets.get(2)));

    // No need for a prefix map if there isn't any ambiguity about where the embeddings came
    // from.
    relationSets.remove(2);
    prefixes = new GraphCreator(relationSets, "").getSvoPrefixes();
    assertNull(prefixes);
  }

  // Why test this method?  Well, the primary impetus at the moment is that I am a little OCD
  // about coverage statistics.  But in writing the test I discovered there was a bug in the
  // code due to the long trailing zeros (the initial test was off by an order of magnitude), so
  // there actually is a good reason to test this.  And I can also justify this by saying that
  // any change to the number of shards produced should be well thought out.  I want a change to
  // break a test, so that the person changing it will stop and think a bit about whether the
  // change is good.  And then change the test to reflect the better shard numbers.
  public void testGetNumShardsReturnsCorrectShardNumbers() {
    List<RelationSet> relationSets = Lists.newArrayList();
    GraphCreator creator = new GraphCreator(relationSets, "");
    assertEquals(2, creator.getNumShards(4999999));
    assertEquals(3, creator.getNumShards(5000000));
    assertEquals(3, creator.getNumShards(9999999));
    assertEquals(4, creator.getNumShards(10000000));
    assertEquals(4, creator.getNumShards(39999999));
    assertEquals(5, creator.getNumShards(40000000));
    assertEquals(5, creator.getNumShards(99999999));
    assertEquals(6, creator.getNumShards(100000000));
    assertEquals(6, creator.getNumShards(149999999));
    assertEquals(7, creator.getNumShards(150000000));
    assertEquals(7, creator.getNumShards(249999999));
    assertEquals(8, creator.getNumShards(250000000));
    assertEquals(8, creator.getNumShards(349999999));
    assertEquals(9, creator.getNumShards(450000000));
    assertEquals(9, creator.getNumShards(499999999));
    assertEquals(10, creator.getNumShards(500000000));
  }
}
