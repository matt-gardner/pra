package edu.cmu.ml.rtw.pra.features;

import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;

import edu.cmu.graphchi.ChiEdge;
import edu.cmu.graphchi.ChiVertex;
import edu.cmu.graphchi.EmptyType;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHash;
import gnu.trove.TIntHashSet;

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
   * Setting needEdgeTypeMaps to false allows you to bypass some pretty time-consuming processing,
   * if you know you won't need it.  Basically, the PathFinder doesn't need it, but the
   * PathFollower does, in current implementations.  If you pass in false and then call methods
   * that depend on it being true, you _will_ cause ArrayIndexOutOfBounds exceptions.
   */
  public Vertex(ChiVertex<EmptyType, Integer> chiVertex, boolean needEdgeTypeMaps) {
    id = chiVertex.getId();
    numEdges = chiVertex.numEdges();
    numInEdges = chiVertex.numInEdges();
    numOutEdges = chiVertex.numOutEdges();
    allEdgeNodes = new int[numEdges];
    allEdgeTypes = new int[numEdges];
    inEdgeNodes = new int[numInEdges];
    inEdgeTypes = new int[numInEdges];
    outEdgeNodes = new int[numOutEdges];
    outEdgeTypes = new int[numOutEdges];
    TIntHashSet seenEdgeTypes = new TIntHashSet();
    TIntHashSet seenInEdgeTypes = new TIntHashSet();
    TIntHashSet seenOutEdgeTypes = new TIntHashSet();
    for (int edgeNum = 0; edgeNum < numInEdges; edgeNum++) {
      ChiEdge<Integer> edge = chiVertex.inEdge(edgeNum);
      int vertexId = edge.getVertexId();
      int type = edge.getValue();
      allEdgeNodes[edgeNum] = vertexId;
      allEdgeTypes[edgeNum] = type;
      inEdgeNodes[edgeNum] = vertexId;
      inEdgeTypes[edgeNum] = type;
      if (needEdgeTypeMaps) {
        seenEdgeTypes.add(type);
        seenInEdgeTypes.add(type);
      }
    }
    for (int edgeNum = 0; edgeNum < numOutEdges; edgeNum++) {
      ChiEdge<Integer> edge = chiVertex.outEdge(edgeNum);
      int vertexId = edge.getVertexId();
      int type = edge.getValue();
      allEdgeNodes[edgeNum + numInEdges] = vertexId;
      allEdgeTypes[edgeNum + numInEdges] = type;
      outEdgeNodes[edgeNum] = vertexId;
      outEdgeTypes[edgeNum] = type;
      if (needEdgeTypeMaps) {
        seenEdgeTypes.add(type);
        seenOutEdgeTypes.add(type);
      }
    }
    if (needEdgeTypeMaps) {
      inEdgeTypeSet = seenInEdgeTypes.toArray();
      Arrays.sort(inEdgeTypeSet);
      outEdgeTypeSet = seenOutEdgeTypes.toArray();
      Arrays.sort(outEdgeTypeSet);
      edgeTypeSet = seenEdgeTypes.toArray();
      Arrays.sort(edgeTypeSet);
      edgeTypeMap = new int[edgeTypeSet.length][2][];
      initializeEdgeTypeMap(inEdgeNodes, inEdgeTypes, outEdgeNodes, outEdgeTypes);
    } else {
      inEdgeTypeSet = new int[seenInEdgeTypes.size()];
      outEdgeTypeSet = new int[seenOutEdgeTypes.size()];
      edgeTypeSet = new int[seenEdgeTypes.size()];
      edgeTypeMap = new int[edgeTypeSet.length][2][];
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
    // We avoid using the edge map here, even if it might be slightly faster, as initializing it is
    // optional.  But this simple sum is probably fast enough for most nodes that it's not a huge
    // deal to do it.
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
    if (edgeTypeSet.length == 0) return -1;
    int top = edgeTypeSet.length - 1;
    int bottom = 0;
    while (top > bottom) {
      int index = (top + bottom) / 2;
      if (edgeTypeSet[index] > edgeType) {
        top = index - 1;
        if (top < 0) return -1;
      } else if (edgeTypeSet[index] < edgeType) {
        bottom = index + 1;
        if (bottom >= edgeTypeSet.length) return -1;
      } else {
        return index;
      }
    }
    if (edgeTypeSet[top] == edgeType) return top;
    return -1;
  }

  @VisibleForTesting
  protected void initializeEdgeTypeMap(int[] inEdgeNodes,
                                       int[] inEdgeTypes,
                                       int[] outEdgeNodes,
                                       int[] outEdgeTypes) {
    TIntArrayList[][] tempEdgeTypeMap = new TIntArrayList[edgeTypeSet.length][];
    for (int i = 0; i < edgeTypeSet.length; i++) {
      edgeTypeMap[i] = new int[2][];
      tempEdgeTypeMap[i] = new TIntArrayList[2];
      tempEdgeTypeMap[i][0] = new TIntArrayList();
      tempEdgeTypeMap[i][1] = new TIntArrayList();
    }
    for (int edgeNum = 0; edgeNum < inEdgeNodes.length; edgeNum++) {
      tempEdgeTypeMap[getEdgeTypeIndex(inEdgeTypes[edgeNum])][1].add(inEdgeNodes[edgeNum]);
    }
    for (int edgeNum = 0; edgeNum < outEdgeNodes.length; edgeNum++) {
      tempEdgeTypeMap[getEdgeTypeIndex(outEdgeTypes[edgeNum])][0].add(outEdgeNodes[edgeNum]);
    }
    for (int i = 0; i < edgeTypeSet.length; i++) {
      if (tempEdgeTypeMap[i][1].size() > 0) edgeTypeMap[i][1] = tempEdgeTypeMap[i][1].toNativeArray();
      if (tempEdgeTypeMap[i][0].size() > 0) edgeTypeMap[i][0] = tempEdgeTypeMap[i][0].toNativeArray();
    }
  }
}
