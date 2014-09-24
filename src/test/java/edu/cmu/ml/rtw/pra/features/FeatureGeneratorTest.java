package edu.cmu.ml.rtw.pra.features;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.pra.experiments.DatasetFactory;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.Pair;
import edu.cmu.ml.rtw.users.matt.util.TestUtil;

public class FeatureGeneratorTest extends TestCase {

    private Pair<Pair<Integer, Integer>, Integer> newTriple(int x, int y, int z) {
        return new Pair<Pair<Integer, Integer>, Integer>(new Pair<Integer, Integer>(x, y), z);
    }

    public void testCollapseInverses() {
        PathTypeFactory factory = new FakePathTypeFactory();
        // The paths here are of the form -1-2-3-.
        Map<PathType, Integer> pathCounts = Maps.newHashMap();
        pathCounts.put(factory.fromString("-1-2-3-"), 2);
        pathCounts.put(factory.fromString("-1-2-3- INVERSE"), 2);

        Map<Integer, Integer> inverses = Maps.newHashMap();
        inverses.put(1, 2);

        FeatureGenerator generator = new FeatureGenerator(new PraConfig.Builder()
                                                                .setPathTypeFactory(factory)
                                                                .build());
        Map<PathType, Integer> collapsed = generator.collapseInverses(pathCounts, inverses);
        assertEquals(1, collapsed.size());
        assertEquals(4, collapsed.get(factory.fromString("-1-2-3- INVERSE")).intValue());
    }

    public void testCollapseInversesInCountMap() {
        PathTypeFactory factory = new FakePathTypeFactory();
        // The paths here are of the form -1-2-3-.
        Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCountMap = Maps.newHashMap();
        Pair<Integer, Integer> pair = new Pair<Integer, Integer>(1, 1);
        Map<PathType, Integer> pathCounts = Maps.newHashMap();
        pathCountMap.put(pair, pathCounts);
        pathCounts.put(factory.fromString("-1-2-3-"), 2);
        pathCounts.put(factory.fromString("-1-2-3- INVERSE"), 2);

        Map<Integer, Integer> inverses = Maps.newHashMap();
        inverses.put(1, 2);

        FeatureGenerator generator = new FeatureGenerator(new PraConfig.Builder()
                                                                .setPathTypeFactory(factory)
                                                                .build());
        Map<Pair<Integer, Integer>, Map<PathType, Integer>> collapsed =
            generator.collapseInversesInCountMap(pathCountMap, inverses);
        assertEquals(1, collapsed.size());
        assertEquals(4, collapsed.get(pair).get(factory.fromString("-1-2-3- INVERSE")).intValue());
    }

    public void testCreateEdgesToExclude() throws IOException {
        String node1 = "node1";
        String node2 = "node2";
        String node3 = "node3";
        String node4 = "node4";
        Dictionary nodeDict = new Dictionary();
        int node1Index = nodeDict.getIndex(node1);
        int node2Index = nodeDict.getIndex(node2);
        int node3Index = nodeDict.getIndex(node3);
        int node4Index = nodeDict.getIndex(node4);
        List<Integer> unallowedEdges = Arrays.asList(1, 3, 2);
        PraConfig config = new PraConfig.Builder().setUnallowedEdges(unallowedEdges).build();
        FeatureGenerator generator = new FeatureGenerator(config);
        String dataFile = "";
        dataFile += node1 + "\t" + node2 + "\n";
        dataFile += node3 + "\t" + node4 + "\n";
        BufferedReader reader = new BufferedReader(new StringReader(dataFile));
        Dataset data = new DatasetFactory().fromReader(reader, new Dictionary());

        // Test the basic case - we have a dataset, and a list of edges.  We should create a list
        // that contains the cross product between them.
        List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude =
                generator.createEdgesToExclude(data);
        assertEquals(6, edgesToExclude.size());
        TestUtil.assertCount(edgesToExclude, newTriple(node1Index, node2Index, 1), 1);
        TestUtil.assertCount(edgesToExclude, newTriple(node1Index, node2Index, 3), 1);
        TestUtil.assertCount(edgesToExclude, newTriple(node1Index, node2Index, 2), 1);
        TestUtil.assertCount(edgesToExclude, newTriple(node3Index, node4Index, 1), 1);
        TestUtil.assertCount(edgesToExclude, newTriple(node3Index, node4Index, 3), 1);
        TestUtil.assertCount(edgesToExclude, newTriple(node3Index, node4Index, 2), 1);

        // Test with a null dataset, which can happen occasionally (particularly, in
        // OnlinePraPredictor).
        edgesToExclude = generator.createEdgesToExclude(null);
        assertEquals(0, edgesToExclude.size());

        // Actually, it's not a null dataset, it's a dataset with no targets, only sources.  So
        // let's test that instance.
        data = new Dataset.Builder().setPositiveSources(
                Arrays.asList(node1Index, node3Index)).build();
        edgesToExclude = generator.createEdgesToExclude(data);
        assertEquals(0, edgesToExclude.size());
    }
}
