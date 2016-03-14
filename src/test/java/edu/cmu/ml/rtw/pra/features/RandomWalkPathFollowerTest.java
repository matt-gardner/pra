package edu.cmu.ml.rtw.pra.features;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.walks.LongWalkArray;
import edu.cmu.ml.rtw.pra.data.NodePairInstance;
import edu.cmu.ml.rtw.pra.experiments.Outputter;
import edu.cmu.ml.rtw.pra.features.FakePathType;
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk;
import com.mattg.util.FakeRandom;
import com.mattg.util.FileUtil;
import com.mattg.util.MapUtil;
import com.mattg.util.Pair;
import com.mattg.util.TestUtil;

public class RandomWalkPathFollowerTest extends TestCase {
    private List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude = Lists.newArrayList();
    private List<NodePairInstance> instances = Lists.newArrayList();
    private List<PathType> paths = Lists.newArrayList();
    private FakeChiVertex chiVertex = new FakeChiVertex(1);
    private Vertex vertex;
    private RandomWalkPathFollower follower;
    private FakeRandom random = new FakeRandom();
    private FakeDrunkardContext context = new FakeDrunkardContext();
    private Map<Integer, List<Integer>> inEdgeMap = Maps.newHashMap();
    private Map<Integer, List<Integer>> outEdgeMap = Maps.newHashMap();
    private GraphOnDisk graph;

    @Override
    public void setUp() {
        paths.add(new FakePathType("fake"));
        addEdgeToExclude(1, 2, 1, edgesToExclude);
        chiVertex.addInEdge(3, 3);
        chiVertex.addInEdge(2, 1);
        chiVertex.addOutEdge(1, 1);
        chiVertex.addOutEdge(2, 1);
        vertex = new Vertex(chiVertex);
        graph = new GraphOnDisk("src/test/resources/", Outputter.justLogger(), new FileUtil());
        instances.add(new NodePairInstance(1, 2, true, graph));
        follower = new RandomWalkPathFollower(graph,
                                              instances,
                                              Sets.newHashSet(2),
                                              SingleEdgeExcluder.fromJava(edgesToExclude),
                                              paths,
                                              0,
                                              MatrixRowPolicy.EVERYTHING,
                                              true);
    }

    @Override
    public void tearDown() {
        edgesToExclude.clear();
        instances.clear();
        paths.clear();
        inEdgeMap.clear();
        outEdgeMap.clear();
        chiVertex = new FakeChiVertex(1);
        vertex = null;
    }

    public void testProcessWalks() {
        // We just test one walk here, so this looks a lot like the processSingleWalk test, but we
        // can check a couple of additional things.
        int pathType = 0;
        int hopNum = 0;
        int sourceId = 0;
        boolean trackBit = false;
        int off = 0;
        long walk = RandomWalkPathFinder.Manager.encode(pathType, hopNum, sourceId, trackBit, off);
        LongWalkArray array = new LongWalkArray(new long[]{walk});

        // First check that nothing happens if we have no edges.
        FakeChiVertex emptyVertex = new FakeChiVertex(1);
        context.dieIfCalled();
        follower.processWalksAtVertex(array, emptyVertex, context, random);
        context.testFinished();

        // Now make sure the right thing happens with the one walk we have (basically a quick
        // repeat of the main flow of processSingleWalk).
        int nextVertex = 3;
        ((FakePathType) paths.get(0)).setNextVertex(nextVertex);
        long newWalk = RandomWalkPathFinder.Manager.encode(pathType, hopNum+1, sourceId, trackBit, off);
        context.setExpectations(false, newWalk, nextVertex, trackBit);
        follower.processWalksAtVertex(array, chiVertex, context, random);
        context.testFinished();
    }

    public void testProcessSingleWalk() {
        Map<Integer, List<Integer>> inEdgeMap = Maps.newHashMap();
        Map<Integer, List<Integer>> outEdgeMap = Maps.newHashMap();

        int pathType = 0;
        int hopNum = 0;
        int sourceId = 0;
        boolean trackBit = false;
        int off = 0;
        long walk = RandomWalkPathFinder.Manager.encode(pathType, hopNum, sourceId, trackBit, off);
        long[] walks = new long[1];
        walks[0] = walk;
        PathTypeVertexCache[][] cache = follower.initializePathTypeVertexCaches(vertex, walks);

        // Test that a successful walk is forwarded correctly.
        int nextVertex = 3;
        ((FakePathType) paths.get(0)).setNextVertex(nextVertex);
        long newWalk = RandomWalkPathFinder.Manager.encode(pathType, hopNum+1, sourceId, trackBit, off);
        context.setExpectations(false, newWalk, nextVertex, trackBit);
        follower.processSingleWalk(walk, vertex, context, random, cache);
        context.testFinished();

        // And test that an unsuccessful walk is reset.
        nextVertex = -1;
        ((FakePathType) paths.get(0)).setNextVertex(nextVertex);
        newWalk = RandomWalkPathFinder.Manager.encode(pathType, 0, sourceId, trackBit, off);
        context.setExpectationsForReset(newWalk, trackBit);
        follower.processSingleWalk(walk, vertex, context, random, cache);
        context.testFinished();
    }

    // If we get as input a source that wasn't in the graph, we should just ignore it.  That is,
    // say we're querying on a node that we didn't actually have when we created the graph, so we
    // had to add it to the node dict.  In that case, we should just drop the node, instead of
    // crashing, which is what the code currently does as of writing this test.
    public void testIgnoresNewSources() {
        instances.add(new NodePairInstance(10000, 20000, true, graph));
        follower = new RandomWalkPathFollower(graph,
                                              instances,
                                              Sets.newHashSet(2),
                                              SingleEdgeExcluder.fromJava(edgesToExclude),
                                              paths,
                                              0,
                                              MatrixRowPolicy.EVERYTHING,
                                              true);
        // We don't care about the results, we just want to be sure that this actually runs.
        follower.execute();
    }

    // TODO(matt): this should go away and be replaced by a fake edge excluder.
    private void addEdgeToExclude(int source,
                                  int target,
                                  int type,
                                  List<Pair<Pair<Integer, Integer>, Integer>> edges) {
        edges.add(new Pair<Pair<Integer, Integer>, Integer>(new Pair<Integer, Integer>(source,
                                                                                       target),
                                                            type));
    }
}
