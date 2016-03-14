package edu.cmu.ml.rtw.pra.features;

import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory.VectorPathType;
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory;
import edu.cmu.ml.rtw.pra.graphs.Node;
import com.mattg.util.FileUtil;
import com.mattg.util.MutableConcurrentDictionary;
import com.mattg.util.FakeRandom;
import com.mattg.util.Vector;

public class VectorPathTypeFactoryTest extends TestCase {
    private Vector vector1 = new Vector(new double[]{1,2,3});
    private Vector vector2 = new Vector(new double[]{-1,-2,-3});
    private Vector vector3 = new Vector(new double[]{1,3,3});
    private Map<Integer, Vector> embeddings = Maps.newHashMap();
    private MutableConcurrentDictionary edgeDict = new MutableConcurrentDictionary(false, new FileUtil());
    private GraphInMemory graph = new GraphInMemory(new Node[0], null, edgeDict);

    @Override
    public void setUp() {
        edgeDict.getIndex("1");
        edgeDict.getIndex("2");
        edgeDict.getIndex("3");
        embeddings.clear();
        vector1.normalize();
        vector2.normalize();
        vector3.normalize();
    }

    public void testEmptyPathType() {
        VectorPathTypeFactory factory = new VectorPathTypeFactory(graph, embeddings, 1, 0.5);
        PathType type12 = factory.fromString("-1-2-");
        assertEquals(factory.emptyPathType(), factory.emptyPathType());
        assertFalse(type12.equals(factory.emptyPathType()));
    }

    public void testCacheVertexInformationDoesNotCrashOnHighHopNum() {
        FakeChiVertex chiVertex = new FakeChiVertex(1);
        chiVertex.addOutEdge(1, 1);
        chiVertex.addOutEdge(2, 2);
        Vertex vertex = new Vertex(chiVertex);
        VectorPathTypeFactory factory = new VectorPathTypeFactory(graph, embeddings, 1, 0.5);
        VectorPathType pathType = (VectorPathType) factory.fromString("-1-2-");
        assertNull(pathType.cacheVertexInformation(vertex, 1000));
    }

    public void testGetNextEdgeTypeWorksCorrectly() {
        embeddings.put(1, vector1);
        embeddings.put(2, vector2);
        VectorPathTypeFactory factory = new VectorPathTypeFactory(graph, embeddings, 1, 0.5);
        VectorPathType pathType = (VectorPathType) factory.fromString("-1-2-");

        FakeChiVertex chiVertex = new FakeChiVertex(1);
        chiVertex.addOutEdge(1, 1);
        chiVertex.addOutEdge(2, 2);
        Vertex vertex = new Vertex(chiVertex);

        FakeRandom random = new FakeRandom();

        int hopNum = 0;
        int sourceId = 0;

        PathTypeVertexCache cache = pathType.cacheVertexInformation(vertex, hopNum);

        // First the simplest case: two edge types, known dot product, make sure we take the right
        // one.
        random.setNextDouble(.1);
        assertEquals(1, pathType.getNextEdgeType(hopNum, vertex, random, cache));

        // We should reset if we draw a 1 from the random number generator.
        random.setNextDouble(1);
        assertEquals(-1, pathType.getNextEdgeType(hopNum, vertex, random, cache));

        // If there is no embedding for the path type, we return the path type index itself.
        embeddings.clear();
        factory = new VectorPathTypeFactory(graph, embeddings, 1, 0.5);
        pathType = (VectorPathType) factory.fromString("-1-2-");
        cache = pathType.cacheVertexInformation(vertex, hopNum);
        assertEquals(1, pathType.getNextEdgeType(hopNum, vertex, random, cache));

        // Now we'll use a vector that's pretty close to the original, using in edges instead of
        // out edges.  And we throw in an edge type without embeddings, just for kicks.
        embeddings.put(1, vector1);
        embeddings.put(2, vector3);
        cache = pathType.cacheVertexInformation(vertex, hopNum);
        factory = new VectorPathTypeFactory(graph, embeddings, 1, 0.5);
        pathType = (VectorPathType) factory.fromString("-_1-2-");

        chiVertex = new FakeChiVertex(1);
        chiVertex.addInEdge(1, 1);
        chiVertex.addInEdge(2, 2);
        chiVertex.addInEdge(2, 3);
        vertex = new Vertex(chiVertex);
        cache = pathType.cacheVertexInformation(vertex, hopNum);
        // We should have close to a 50% chance of picking this other edge type.
        random.setNextDouble(.5);
        assertEquals(2, pathType.getNextEdgeType(hopNum, vertex, random, cache));

        // And if there's only one edge type available, and it's the right one, we should always
        // take it, no matter what random number we draw.
        chiVertex = new FakeChiVertex(1);
        chiVertex.addInEdge(1, 1);
        vertex = new Vertex(chiVertex);
        cache = pathType.cacheVertexInformation(vertex, hopNum);
        random.setNextDouble(0.0);
        assertEquals(1, pathType.getNextEdgeType(hopNum, vertex, random, cache));
        random.setNextDouble(0.5);
        assertEquals(1, pathType.getNextEdgeType(hopNum, vertex, random, cache));
        random.setNextDouble(1.0);
        assertEquals(1, pathType.getNextEdgeType(hopNum, vertex, random, cache));

        // But if it's a different edge type, we _should_ take into the reset probability.
        chiVertex = new FakeChiVertex(1);
        chiVertex.addInEdge(1, 2);
        vertex = new Vertex(chiVertex);
        cache = pathType.cacheVertexInformation(vertex, hopNum);
        random.setNextDouble(0.0);
        assertEquals(2, pathType.getNextEdgeType(hopNum, vertex, random, cache));
        random.setNextDouble(1.0);
        assertEquals(-1, pathType.getNextEdgeType(hopNum, vertex, random, cache));
    }
}
