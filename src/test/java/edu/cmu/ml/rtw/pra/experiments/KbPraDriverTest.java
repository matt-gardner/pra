package edu.cmu.ml.rtw.pra.experiments;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil;
import edu.cmu.ml.rtw.users.matt.util.TestUtil;

public class KbPraDriverTest extends TestCase {
  private KbPraDriver driver = new KbPraDriver();

  public void testCreateUnallowedEdges() throws IOException {
    String relation = "testRelation";
    String relation2 = "testRelationNoInverse";
    String inverse = "testInverse";
    String embedded1 = "embedded1";
    String embedded2 = "embedded2";
    String embedded3 = "embedded3";
    Dictionary edgeDict = new Dictionary();
    Map<Integer, Integer> inverses = new HashMap<Integer, Integer>();
    inverses.put(edgeDict.getIndex(relation), edgeDict.getIndex(inverse));
    Map<String, List<String>> embeddings = new HashMap<String, List<String>>();
    embeddings.put(relation, Arrays.asList(embedded1, embedded2));
    embeddings.put(inverse, Arrays.asList(embedded2, embedded3));

    // Test the general case, with inverses and embeddings.  Note that we should always include
    // the relation itself, even if there are embeddings.  Sometimes we use both the original
    // edge and the embedding as edges in the graph.  If the original edge is still there, this
    // will stop it, and if it's not there, having it in this list won't hurt anything.
    List<Integer> unallowedEdges = driver.createUnallowedEdges(relation,
                                                               inverses,
                                                               embeddings,
                                                               edgeDict);
    assertEquals(6, unallowedEdges.size());
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(embedded1), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(embedded2), 2);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(embedded3), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(relation), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(inverse), 1);

    // Test with no known inverses.
    unallowedEdges = driver.createUnallowedEdges(relation,
                                                 new HashMap<Integer, Integer>(),
                                                 embeddings,
                                                 edgeDict);
    assertEquals(3, unallowedEdges.size());
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(embedded1), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(embedded2), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(relation), 1);

    // Test with no embeddings.
    unallowedEdges = driver.createUnallowedEdges(relation,
                                                 inverses,
                                                 null,
                                                 edgeDict);
    assertEquals(2, unallowedEdges.size());
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(relation), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(inverse), 1);

    // Test a relation that has no inverse.
    unallowedEdges = driver.createUnallowedEdges(relation2,
                                                 inverses,
                                                 null,
                                                 edgeDict);
    assertEquals(1, unallowedEdges.size());
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(relation2), 1);

    // Catching a bug: test when the inverse has no embeddings.
    embeddings.remove(inverse);
    unallowedEdges = driver.createUnallowedEdges(relation,
                                                 inverses,
                                                 embeddings,
                                                 edgeDict);
    assertEquals(4, unallowedEdges.size());
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(embedded1), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(embedded2), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(relation), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(inverse), 1);

    // Catching another bug: when the relation has no embeddings
    embeddings.remove(relation);
    unallowedEdges = driver.createUnallowedEdges(relation,
                                                 inverses,
                                                 embeddings,
                                                 edgeDict);
    assertEquals(2, unallowedEdges.size());
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(relation), 1);
    TestUtil.assertCount(unallowedEdges, edgeDict.getIndex(inverse), 1);
  }

  public void testInitializeSplit() throws IOException {
    String splitsDirectory = "/dev/null/";
    String kbDirectory = "/dev/null/";
    String relation = "/test/fb/relation";
    String crossValidatedRelation = "/CV/fb/relation";
    PraConfig.Builder builder = new PraConfig.Builder();
    FakeDatasetFactory factory = new FakeDatasetFactory();
    FakeFileUtil fileUtil = new FakeFileUtil();

    // Test that when the file exists in the splits directory, we do _not_ do cross validation.
    fileUtil.addExistingFile(splitsDirectory + relation.replace("/", "_"));
    assertEquals(false, driver.initializeSplit(splitsDirectory,
                                               kbDirectory,
                                               relation,
                                               builder,
                                               factory,
                                               fileUtil));
    // And when the file does not exist, we _do_ cross validation.
    fileUtil.setDoubleList(Arrays.asList(0.0));
    assertEquals(true, driver.initializeSplit(splitsDirectory,
                                              kbDirectory,
                                              crossValidatedRelation,
                                              builder,
                                              factory,
                                              fileUtil));
  }
}
