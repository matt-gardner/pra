package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FakeRandom;
import edu.cmu.ml.rtw.users.matt.util.MapUtil;

// This class also tests BaseEdgeSequencePathTypeFactory, which has the implementations of most of
// these methods.
public class BasicPathTypeFactoryTest extends TestCase {
    private BasicPathTypeFactory factory = new BasicPathTypeFactory();
    private PathType type1 = factory.fromString("-1-");
    private PathType type12 = factory.fromString("-1-2-");
    private PathType type1234 = factory.fromString("-1-2-3-4-");
    private PathType type12_3_4 = factory.fromString("-1-2-_3-_4-");
    private PathType type2 = factory.fromString("-2-");
    private PathType type_2 = factory.fromString("-_2-");
    private PathType type43 = factory.fromString("-4-3-");
    private PathType type_4_3 = factory.fromString("-_4-_3-");

    public void testConcatenatePathTypes() {
        assertEquals(type1, factory.concatenatePathTypes(type1, factory.emptyPathType()));
        assertEquals(type2, factory.concatenatePathTypes(factory.emptyPathType(), type_2));
        assertEquals(type12, factory.concatenatePathTypes(type1, type_2));
        assertEquals(type1234, factory.concatenatePathTypes(type12, type_4_3));
        assertEquals(type12_3_4, factory.concatenatePathTypes(type12, type43));
    }

    public void testEmptyPathType() {
        assertEquals(factory.emptyPathType(), factory.emptyPathType());
        assertFalse(type12.equals(factory.emptyPathType()));
    }

    public void testEconde() {
        // The path we create is 1 -_1-> 2 -2-> 3 -3-> 4.  So the BasicPathType we should get from
        // this is -1-2-3-.
        Path path = new Path(1, 5);
        path.addHop(2, 1, true);
        path.addHop(3, 2, false);
        path.addHop(4, 3, false);

        PathType[] pathTypes = factory.encode(path);
        assertEquals(1, pathTypes.length);
        assertEquals(pathTypes[0], factory.fromString("-_1-2-3-"));
    }

    public void testCollapseEdgeInverses() {
        // This method should be an identity.
        PathType pathType = factory.fromString("-1-");
        Map<Integer, Integer> inverses = Maps.newHashMap();
        assertTrue(pathType == factory.collapseEdgeInverses(pathType, inverses));
    }

    public void testNextHop() {
        PathType pathType = factory.fromString("-1-2-");

        FakeEdgeExcluder excluder = new FakeEdgeExcluder();
        FakeChiVertex chiVertex = new FakeChiVertex(1);
        chiVertex.addInEdge(3, 3);
        chiVertex.addInEdge(2, 1);
        chiVertex.addOutEdge(1, 1);
        chiVertex.addOutEdge(2, 1);
        chiVertex.addOutEdge(3, 1);
        Vertex vertex = new Vertex(chiVertex);

        FakeDrunkardContext context = new FakeDrunkardContext();
        FakeRandom random = new FakeRandom();

        int hopNum = 0;
        int sourceId = 0;

        // Ok, first, just make sure we get to the right next vertex.
        random.setNextInt(2);
        assertEquals(3, pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null));
        random.setNextInt(0);

        // Now, make sure we reset if the walk ends up somewhere it's not allowed to be.
        excluder.setShouldExclude(true);
        assertEquals(-1, pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null));
        excluder.setShouldExclude(false);

        // Make sure we reset if the hop number is too high.
        hopNum = 10;
        assertEquals(-1, pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null));
        hopNum = 0;

        // Make sure we reset if there are no edges that match the path type.
        pathType = factory.fromString("-10-2-");
        assertEquals(-1, pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null));

        // Now try some with a second path type, that has a reverse edge.
        pathType = factory.fromString("-_3-2-");
        assertEquals(3, pathType.nextHop(hopNum, sourceId, vertex, random, excluder, null));
    }

    public void testIsLastHop() {
        PathType pathType = factory.fromString("-1-2-3-");
        assertEquals(true, pathType.isLastHop(2));
        assertEquals(false, pathType.isLastHop(1));
    }

    public void testBasicPathTypeStrings() {
        String pathDescription = "-1-_2-3-";
        PathType pathType = factory.fromString(pathDescription);
        assertEquals(pathDescription, pathType.encodeAsString());
        assertEquals(pathDescription, pathType.toString());
        Dictionary edgeDict = new Dictionary();
        edgeDict.getIndex("r1");
        edgeDict.getIndex("r2");
        edgeDict.getIndex("r3");
        String humanReadable = "-r1-_r2-r3-";
        assertEquals(humanReadable, pathType.encodeAsHumanReadableString(edgeDict));
    }
}
