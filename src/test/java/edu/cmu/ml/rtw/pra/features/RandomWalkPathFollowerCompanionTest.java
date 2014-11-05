package edu.cmu.ml.rtw.pra.features;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import junit.framework.TestCase;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.walks.distributions.DiscreteDistribution;
import edu.cmu.ml.rtw.users.matt.util.TestUtil;
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function;

public class RandomWalkPathFollowerCompanionTest extends TestCase {

    public void testAcceptableRow() throws RemoteException {
        Set<Integer> allowedTargets = Sets.newHashSet();
        allowedTargets.add(2);
        allowedTargets.add(3);
        allowedTargets.add(10);
        final RandomWalkPathFollowerCompanion companion = new RandomWalkPathFollowerCompanion(
                1,
                1024,
                new FakeVertexIdTranslate(),
                new PathType[1],
                MatrixRowPolicy.ALL_TARGETS,
                allowedTargets,
                true);
        final Set<Integer> sourceTargets = Sets.newHashSet();
        sourceTargets.add(2);
        final Set<Integer> allTargets = Sets.newHashSet(sourceTargets);
        allTargets.add(3);

        companion.setAcceptPolicy(MatrixRowPolicy.EVERYTHING);
        assertEquals(true, companion.acceptableRow(1, 20, sourceTargets, allTargets));

        companion.setAcceptPolicy(MatrixRowPolicy.ALL_TARGETS);
        assertEquals(true, companion.acceptableRow(1, 10, sourceTargets, allTargets));
        assertEquals(false, companion.acceptableRow(1, 20, sourceTargets, allTargets));
        companion.setAllowedTargets(null);
        assertEquals(false, companion.acceptableRow(1, 10, sourceTargets, allTargets));

        companion.setAcceptPolicy(MatrixRowPolicy.PAIRED_TARGETS_ONLY);
        assertEquals(true, companion.acceptableRow(1, 2, sourceTargets, allTargets));
        assertEquals(false, companion.acceptableRow(1, 3, sourceTargets, allTargets));

        companion.setAcceptPolicy(null);
        TestUtil.expectError(RuntimeException.class, new Function() {
            @Override
            public void call() {
                companion.acceptableRow(1, 2, sourceTargets, allTargets);
            }
        });
    }

    public void testGetFeatureMatrix() throws RemoteException {
        final RandomWalkPathFollowerCompanion companion = new RandomWalkPathFollowerCompanion(
                1,
                1024,
                new FakeVertexIdTranslate(),
                new PathType[1],
                MatrixRowPolicy.EVERYTHING,  // so we don't have to worry about matching
                null,
                true);
        // We'll have one source, one path type, and two targets in this test.
        ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, DiscreteDistribution>> dists =
                new ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, DiscreteDistribution>>();
        int sourceNode = 1;
        int pathType = 1;
        int targetNode1 = 2;
        int targetNode2 = 3;
        DiscreteDistribution targetNodeDist = new DiscreteDistribution(
                new int[]{targetNode1, targetNode1, targetNode1, targetNode2});
        dists.put(sourceNode, new ConcurrentHashMap<Integer, DiscreteDistribution>());
        dists.get(sourceNode).put(pathType, targetNodeDist);
        companion.setDistributions(dists);

        Map<Integer, Set<Integer>> sourcesMap = Maps.newHashMap();
        Set<Integer> targets = Sets.newHashSet();
        targets.add(targetNode1);
        targets.add(targetNode2);
        sourcesMap.put(sourceNode, targets);
        FeatureMatrix matrix = companion.getFeatureMatrix(sourcesMap);
        MatrixRow firstRow = new MatrixRow(sourceNode,
                                           targetNode1,
                                           new int[]{pathType},
                                           new double[]{.75});
        MatrixRow secondRow = new MatrixRow(sourceNode,
                                            targetNode2,
                                            new int[]{pathType},
                                            new double[]{.25});
        assertEquals(2, matrix.size());
        TestUtil.assertContains(matrix.getRows(), firstRow);
        TestUtil.assertContains(matrix.getRows(), secondRow);
    }
}
