package edu.cmu.ml.rtw.pra.features;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.ChiEdge;
import edu.cmu.graphchi.ChiVertex;
import edu.cmu.graphchi.EmptyType;
import edu.cmu.ml.rtw.users.matt.util.MapUtil;

/**
 * A nicer representation for the processing we do at each vertex than a ChiVertex.
 *
 * The reason it is a nicer representation is because we trade memory for faster processing - there
 * are a lot of duplicate variables in here, but it speeds up the inner loop of our code if we use
 * a bit more memory to have faster access to those variables.
 */
public class Vertex {
    private final int id;
    private final int numEdges;
    private final int numInEdges;
    private final int numOutEdges;
    private final int[] allEdgeNodes;
    private final int[] allEdgeTypes;
    private final int[] inEdgeNodes;
    private final int[] outEdgeNodes;
    private final int[] inEdgeTypes;
    private final int[] outEdgeTypes;
    private final int[] edgeTypeSet;
    private final int[] inEdgeTypeSet;
    private final int[] outEdgeTypeSet;
    private final int[][][] edgeTypeMap;

    public Vertex(ChiVertex<EmptyType, Integer> chiVertex) {
        this(chiVertex, true);
    }

    /**
     * Setting needEdgeTypeMaps to false allows you to bypass some pretty time-consuming
     * processing, if you know you won't need it.  Basically, the PathFinder doesn't need it, but
     * the PathFollower does, in current implementations.  If you pass in false and then call
     * methods that depend on it being true, you _will_ cause ArrayIndexOutOfBounds exceptions.
     */
    public Vertex(ChiVertex<EmptyType, Integer> chiVertex, boolean needEdgeTypeMaps) {
        id = chiVertex.getId();
        numEdges = chiVertex.numEdges();
        numInEdges = chiVertex.numInEdges();
        numOutEdges = chiVertex.numOutEdges();
        allEdgeNodes = new int[numEdges];
        allEdgeTypes = new int[numEdges];
        inEdgeNodes = new int[chiVertex.numInEdges()];
        inEdgeTypes = new int[chiVertex.numInEdges()];
        outEdgeNodes = new int[chiVertex.numOutEdges()];
        outEdgeTypes = new int[chiVertex.numOutEdges()];
        Set<Integer> seenEdgeTypes = Sets.newHashSet();
        Set<Integer> seenInEdgeTypes = Sets.newHashSet();
        Set<Integer> seenOutEdgeTypes = Sets.newHashSet();
        for (int edgeNum = 0; edgeNum < chiVertex.numInEdges(); edgeNum++) {
            ChiEdge<Integer> edge = chiVertex.inEdge(edgeNum);
            allEdgeNodes[edgeNum] = edge.getVertexId();
            allEdgeTypes[edgeNum] = edge.getValue();
            inEdgeNodes[edgeNum] = edge.getVertexId();
            inEdgeTypes[edgeNum] = edge.getValue();
            if (needEdgeTypeMaps) {
                seenEdgeTypes.add(edge.getValue());
                seenInEdgeTypes.add(edge.getValue());
            }
        }
        for (int edgeNum = 0; edgeNum < chiVertex.numOutEdges(); edgeNum++) {
            ChiEdge<Integer> edge = chiVertex.outEdge(edgeNum);
            allEdgeNodes[edgeNum + numInEdges] = edge.getVertexId();
            allEdgeTypes[edgeNum + numInEdges] = edge.getValue();
            outEdgeNodes[edgeNum] = edge.getVertexId();
            outEdgeTypes[edgeNum] = edge.getValue();
            if (needEdgeTypeMaps) {
                seenEdgeTypes.add(edge.getValue());
                seenOutEdgeTypes.add(edge.getValue());
            }
        }
        inEdgeTypeSet = new int[seenInEdgeTypes.size()];
        outEdgeTypeSet = new int[seenOutEdgeTypes.size()];
        edgeTypeSet = new int[seenEdgeTypes.size()];
        edgeTypeMap = new int[edgeTypeSet.length][2][];
        if (needEdgeTypeMaps) {
            List<Integer> inEdgeTypeList = Lists.newArrayList(seenInEdgeTypes);
            Collections.sort(inEdgeTypeList);
            for (int i = 0; i < inEdgeTypeList.size(); i++) {
                inEdgeTypeSet[i] = inEdgeTypeList.get(i);
            }
            List<Integer> outEdgeTypeList = Lists.newArrayList(seenOutEdgeTypes);
            Collections.sort(outEdgeTypeList);
            for (int i = 0; i < outEdgeTypeList.size(); i++) {
                outEdgeTypeSet[i] = outEdgeTypeList.get(i);
            }
            List<Integer> edgeTypeList = Lists.newArrayList(seenEdgeTypes);
            Collections.sort(edgeTypeList);
            for (int i = 0; i < edgeTypeList.size(); i++) {
                edgeTypeSet[i] = edgeTypeList.get(i);
            }
            initializeEdgeTypeMap(chiVertex);
        }
    }

    public int getId() {
        return id;
    }

    public int getNumEdges() {
        return numEdges;
    }

    public int getNumInEdges() {
        return numInEdges;
    }

    public int getNumOutEdges() {
        return numOutEdges;
    }

    public int getEdgeNode(int edgeNum) {
        return allEdgeNodes[edgeNum];
    }

    public int getEdgeType(int edgeNum) {
        return allEdgeTypes[edgeNum];
    }

    public int getNodeEdgeCount(int nodeId, int edgeType) {
        // We avoid using the edge map here, even if it might be slightly faster, as initializing
        // it is optional.  But this simple sum is probably fast enough for most nodes that it's
        // not a huge deal to do it.
        int count = 0;
        for (int i = 0; i < numEdges; i++) {
            if (allEdgeNodes[i] == nodeId && allEdgeTypes[i] == edgeType) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gives an array of possible node ids that correspond to the input edge type and direction.
     */
    public int[] getPossibleNodes(int edgeType, boolean reverse) {
        int edgeTypeIndex = getEdgeTypeIndex(edgeType);
        if (edgeTypeIndex == -1) return null;
        return edgeTypeMap[edgeTypeIndex][reverse ? 1 : 0];
    }

    /**
     * Returns all of the edge types that are either incoming or outgoing from this node.
     */
    public int[] getEdgeTypes() {
        return edgeTypeSet;
    }

    /**
     * Returns all edge types that are incoming to this node.
     */
    public int[] getInEdgeTypes() {
        return inEdgeTypeSet;
    }

    /**
     * Returns all edge types that are outgoing from this node.
     */
    public int[] getOutEdgeTypes() {
        return outEdgeTypeSet;
    }

    @VisibleForTesting
    protected int getEdgeTypeIndex(int edgeType) {
        for (int i = 0; i < edgeTypeSet.length; i++) {
            if (edgeTypeSet[i] == edgeType) return i;
            // The edge types are sorted, so we can break early.  It would be more efficient to do
            // a binary search, maybe, except the number of edge types at each node should be
            // pretty small.
            if (edgeTypeSet[i] > edgeType) return -1;
        }
        return -1;
    }

    @VisibleForTesting
    protected void initializeEdgeTypeMap(ChiVertex<EmptyType, Integer> chiVertex) {
        for (int i = 0; i < edgeTypeSet.length; i++) {
            edgeTypeMap[i] = new int[2][];
        }
        Map<Integer, Map<Boolean, List<Integer>>> edgeMap = Maps.newHashMap();
        for (int edgeNum = 0; edgeNum < chiVertex.numInEdges(); edgeNum++) {
            ChiEdge<Integer> edge = chiVertex.inEdge(edgeNum);
            MapUtil.addValueToTwoKeyList(edgeMap, edge.getValue(), true, edge.getVertexId());
        }
        for (int edgeNum = 0; edgeNum < chiVertex.numOutEdges(); edgeNum++) {
            ChiEdge<Integer> edge = chiVertex.outEdge(edgeNum);
            MapUtil.addValueToTwoKeyList(edgeMap, edge.getValue(), false, edge.getVertexId());
        }
        for (int i = 0; i < edgeTypeSet.length; i++) {
            putNodesIntoEdgeMap(i, true, edgeMap.get(edgeTypeSet[i]).get(true));
            putNodesIntoEdgeMap(i, false, edgeMap.get(edgeTypeSet[i]).get(false));
        }
    }

    private void putNodesIntoEdgeMap(int edgeTypeIndex, boolean reverse, List<Integer> nodes) {
        if (nodes == null) return;
        int reverseIndex = reverse ? 1 : 0;
        edgeTypeMap[edgeTypeIndex][reverseIndex] = new int[nodes.size()];
        int counter = 0;
        for (int node : nodes) {
            edgeTypeMap[edgeTypeIndex][reverseIndex][counter++] = node;
        }
    }
}
