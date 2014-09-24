package edu.cmu.ml.rtw.pra.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Lists;

import edu.cmu.ml.rtw.users.matt.util.Pair;
import edu.cmu.ml.rtw.users.matt.util.TestUtil;

public class SingleEdgeExcluderTest extends TestCase {
    private List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude = Lists.newArrayList();
    private SingleEdgeExcluder excluder = new SingleEdgeExcluder(edgesToExclude);

    @Override
    public void setUp() {
        edgesToExclude.clear();
    }

    public void tearDown() {
    }

    public void testCreateUnallowedWalks() {
        addEdgeToExclude(1, 2, 1, edgesToExclude);
        addEdgeToExclude(2, 3, 1, edgesToExclude);
        addEdgeToExclude(2, 3, 1, edgesToExclude);
        excluder.prepUnallowedWalks(new FakeVertexIdTranslate());
        Map<Integer, Map<Integer, List<Integer>>> unallowedWalks = excluder.getUnallowedWalks();
        assertEquals(3, unallowedWalks.size());
        TestUtil.assertCount(unallowedWalks.get(1).get(2), 1, 1);
        TestUtil.assertCount(unallowedWalks.get(2).get(1), 1, 1);
        TestUtil.assertCount(unallowedWalks.get(2).get(3), 1, 2);
        TestUtil.assertCount(unallowedWalks.get(3).get(2), 1, 2);
    }

    public void testShouldExcludeEdge() {
        addEdgeToExclude(1, 2, 1, edgesToExclude);
        excluder.prepUnallowedWalks(new FakeVertexIdTranslate());
        FakeChiVertex chiVertex = new FakeChiVertex(1);
        chiVertex.addInEdge(3, 3);
        chiVertex.addInEdge(2, 1);
        chiVertex.addOutEdge(1, 1);

        // First we test the simple cases: we should exclude the edge when the source of the walk
        // matches the edge to be excluded, and we're trying to walk across the edge.
        Vertex vertex = new Vertex(chiVertex);
        assertEquals(true, excluder.shouldExcludeEdge(1, 2, vertex, 1));
        assertEquals(true, excluder.shouldExcludeEdge(2, 2, vertex, 1));

        chiVertex.setVertexId(2);
        vertex = new Vertex(chiVertex);
        assertEquals(true, excluder.shouldExcludeEdge(2, 1, vertex, 1));
        assertEquals(true, excluder.shouldExcludeEdge(1, 1, vertex, 1));

        // Now we test some cases where the edge shouldn't be excluded.

        // The walk is between a training (source, target) pair, but the edge type is different.
        assertEquals(false, excluder.shouldExcludeEdge(1, 1, vertex, 2));

        // The (source, target) pair isn't in the training data.
        assertEquals(false, excluder.shouldExcludeEdge(1, 4, vertex, 1));
        assertEquals(false, excluder.shouldExcludeEdge(3, 4, vertex, 1));

        chiVertex.setVertexId(10);
        vertex = new Vertex(chiVertex);
        assertEquals(false, excluder.shouldExcludeEdge(10, 4, vertex, 1));

        // And the trickiest case: there are more edges in the vertex than there are edges to
        // exclude.  This might happen if the KB edges are embedded into a latent space, and so
        // there is overlap in the edge types.  We only want to exclude one edge of each type.
        chiVertex.setVertexId(2);
        chiVertex.addOutEdge(1, 1);
        vertex = new Vertex(chiVertex);
        assertEquals(false, excluder.shouldExcludeEdge(1, 1, vertex, 1));
    }


    private void addEdgeToExclude(int source,
                                  int target,
                                  int type,
                                  List<Pair<Pair<Integer, Integer>, Integer>> edges) {
        edges.add(new Pair<Pair<Integer, Integer>, Integer>(new Pair<Integer, Integer>(source,
                                                                                       target),
                                                            type));
    }
}
