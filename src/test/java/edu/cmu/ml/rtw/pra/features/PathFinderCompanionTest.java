package edu.cmu.ml.rtw.pra.features;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import junit.framework.TestCase;

import com.google.common.collect.Maps;

import edu.cmu.graphchi.walks.distributions.DiscreteDistribution;
import edu.cmu.ml.rtw.users.matt.util.Index;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class PathFinderCompanionTest extends TestCase {
    private BasicPathTypeFactory factory = new BasicPathTypeFactory();
    private PathType type1 = factory.fromString("-1-");
    private PathType type2 = factory.fromString("-2-");
    private PathType type_2 = factory.fromString("-_2-");
    private PathType type_3 = factory.fromString("-_3-");
    private PathType type12 = factory.fromString("-1-2-");
    private PathType type13 = factory.fromString("-1-3-");
    private PathType type22 = factory.fromString("-2-2-");
    private PathType type23 = factory.fromString("-2-3-");
    private Index<PathType> pathDict = new Index<PathType>(factory);
    private int type1Index = pathDict.getIndex(type1);
    private int type2Index = pathDict.getIndex(type2);
    private int type_2Index = pathDict.getIndex(type_2);
    private int type_3Index = pathDict.getIndex(type_3);

    private int sourceNode = 1;
    private int targetNode = 2;
    private Pair<Integer, Integer> sourceTargetPair =
        new Pair<Integer, Integer>(sourceNode, targetNode);
    private int intermediateNode = 3;

    private void setDistributionsForTest(PathFinderCompanion companion) {
        // We'll have one source, one target, and one intermediate node in this test.
        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, DiscreteDistribution>> dists =
                new ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, DiscreteDistribution>>();
        DiscreteDistribution sourceTargetDist = new DiscreteDistribution(
                new int[]{type1Index, type1Index});
        DiscreteDistribution targetSourceDist = new DiscreteDistribution(
                new int[]{type_2Index, type_2Index});
        DiscreteDistribution sourceIntermDist = new DiscreteDistribution(
                new int[]{type2Index, type2Index});
        DiscreteDistribution targetIntermDist = new DiscreteDistribution(
                new int[]{type_3Index, type_3Index});
        dists.put(sourceNode, new ConcurrentHashMap<Integer, DiscreteDistribution>());
        dists.put(targetNode, new ConcurrentHashMap<Integer, DiscreteDistribution>());
        dists.put(intermediateNode, new ConcurrentHashMap<Integer, DiscreteDistribution>());
        dists.get(targetNode).put(sourceNode, sourceTargetDist);  // will give ("-1-", 4)
        dists.get(sourceNode).put(targetNode, targetSourceDist);  // will give ("-2-", 4)
        dists.get(intermediateNode).put(sourceNode, sourceIntermDist);
        dists.get(intermediateNode).put(targetNode, targetIntermDist);  // will give ("-2-3-", 4)
        companion.setDistributions(dists);
    }

    public void testGetPathCounts() throws RemoteException {
        PathFinderCompanion companion = new PathFinderCompanion(1,
                                                                1024,
                                                                new FakeVertexIdTranslate(),
                                                                pathDict,
                                                                factory,
                                                                PathTypePolicy.PAIRED_ONLY);
        setDistributionsForTest(companion);
        Map<PathType, Integer> pathCounts = companion.getPathCounts(Arrays.asList(sourceNode),
                                                                    Arrays.asList(targetNode));
        assertEquals(3, pathCounts.size());
        assertEquals(4, pathCounts.get(type1).intValue());
        assertEquals(4, pathCounts.get(type2).intValue());
        assertEquals(4, pathCounts.get(type23).intValue());
    }

    public void testGetPathCountMap() throws RemoteException {
        PathFinderCompanion companion = new PathFinderCompanion(1,
                                                                1024,
                                                                new FakeVertexIdTranslate(),
                                                                pathDict,
                                                                factory,
                                                                PathTypePolicy.PAIRED_ONLY);
        setDistributionsForTest(companion);
        Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCountMap =
            companion.getPathCountMap(Arrays.asList(sourceNode), Arrays.asList(targetNode));
        assertEquals(1, pathCountMap.size());
        assertEquals(4, pathCountMap.get(sourceTargetPair).get(type1).intValue());
        assertEquals(4, pathCountMap.get(sourceTargetPair).get(type2).intValue());
        assertEquals(4, pathCountMap.get(sourceTargetPair).get(type23).intValue());
    }

    public void testIncrementCounts() throws RemoteException {
        // First the simple case where we just have two strings and a count.
        PathFinderCompanion companion = new PathFinderCompanion(1,
                                                                1024,
                                                                new FakeVertexIdTranslate(),
                                                                pathDict,
                                                                factory,
                                                                PathTypePolicy.PAIRED_ONLY);

        Map<PathType, Integer> pathCounts = Maps.newHashMap();
        companion.incrementCounts(pathCounts, type1, type_2, 2);
        assertEquals(2, pathCounts.get(type12).intValue());
        companion.incrementCounts(pathCounts, type1, type_2, 1);
        assertEquals(3, pathCounts.get(type12).intValue());

        // Now assume we have a distribution and a path.  The distribution here is a distribution
        // of _path types_ that connect two nodes.
        DiscreteDistribution dist = new DiscreteDistribution(
                new int[]{type_2Index, type_2Index, type_3Index});
        pathCounts = Maps.newHashMap();
        companion.incrementCounts(pathCounts, type1, dist);
        assertEquals(4, pathCounts.get(type12).intValue());
        assertEquals(1, pathCounts.get(type13).intValue());

        pathCounts = Maps.newHashMap();
        companion.setPolicy(PathTypePolicy.EVERYTHING);
        companion.incrementCounts(pathCounts, type1, dist);
        assertEquals(8, pathCounts.get(type12).intValue());
        assertEquals(1, pathCounts.get(type13).intValue());

        dist = new DiscreteDistribution( new int[]{type1Index, type1Index, type2Index});
        pathCounts = Maps.newHashMap();
        companion.incrementCounts(pathCounts, dist, type_2);
        assertEquals(8, pathCounts.get(type12).intValue());
        assertEquals(1, pathCounts.get(type22).intValue());

        pathCounts = Maps.newHashMap();
        companion.setPolicy(PathTypePolicy.PAIRED_ONLY);
        companion.incrementCounts(pathCounts, dist, type_2);
        assertEquals(4, pathCounts.get(type12).intValue());
        assertEquals(1, pathCounts.get(type22).intValue());

        // Now we have two distributions that we're combining, one from the source, one from the
        // target.
        DiscreteDistribution sourceDist = new DiscreteDistribution(
                new int[]{type1Index, type1Index, type2Index});
        DiscreteDistribution targetDist = new DiscreteDistribution(
                new int[]{type_2Index, type_2Index, type_3Index});
        pathCounts = Maps.newHashMap();
        companion.incrementCounts(pathCounts, sourceDist, targetDist);
        assertEquals(4, pathCounts.get(type12).intValue());
        assertEquals(2, pathCounts.get(type13).intValue());
        assertEquals(2, pathCounts.get(type22).intValue());
        assertEquals(1, pathCounts.get(type23).intValue());
    }
}
