package edu.cmu.ml.rtw.pra.features;

import java.util.Arrays;

import junit.framework.TestCase;

public class VertexTest extends TestCase {
    // Really all this does is construct a vertex into a bunch of read-only fields.  So
    // construction is about all we have to test.
    public void testConstruction() {
        FakeChiVertex chiVertex = new FakeChiVertex(1);
        chiVertex.addInEdge(3, 3);
        chiVertex.addInEdge(2, 1);
        chiVertex.addOutEdge(1, 1);
        chiVertex.addOutEdge(2, 1);
        chiVertex.addOutEdge(3, 1);
        Vertex vertex = new Vertex(chiVertex);

        assertEquals(1, vertex.getId());
        assertEquals(5, vertex.getNumEdges());
        assertEquals(2, vertex.getNumInEdges());
        assertEquals(3, vertex.getNumOutEdges());
        assertEquals(2, vertex.getEdgeNode(3));
        assertEquals(1, vertex.getEdgeType(3));
        assertEquals(2, vertex.getNodeEdgeCount(2, 1));
        assertEquals(0, vertex.getNodeEdgeCount(20, 1));
        assertEquals(0, vertex.getNodeEdgeCount(2, 10));
        assertTrue(Arrays.equals(new int[]{1, 2, 3}, vertex.getPossibleNodes(1, false)));
        assertTrue(Arrays.equals(new int[]{1, 3}, vertex.getEdgeTypes()));
        assertTrue(Arrays.equals(new int[]{1, 3}, vertex.getInEdgeTypes()));
        assertTrue(Arrays.equals(new int[]{1}, vertex.getOutEdgeTypes()));
        assertEquals(1, vertex.getEdgeTypeIndex(3));
        assertEquals(-1, vertex.getEdgeTypeIndex(-1));
        assertEquals(-1, vertex.getEdgeTypeIndex(2));
    }

    public void testGetEdgeTypeIndexWithLotsOfEdgeTypes() {
        FakeChiVertex chiVertex = new FakeChiVertex(1);
        for (int i = 0; i < 21; i++) {
          chiVertex.addOutEdge(1, i);
        }
        Vertex vertex = new Vertex(chiVertex);
        for (int i = 0; i < 21; i++) {
          assertEquals(i, vertex.getEdgeTypeIndex(i));
        }
    }
}
