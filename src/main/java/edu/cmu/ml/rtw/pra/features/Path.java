package edu.cmu.ml.rtw.pra.features;

import java.util.Arrays;

public class Path {
  private int[] nodes;
  private int[] edges;
  private boolean[] reverse;
  private int startNode;
  private int hops;

  public Path(int startNode, int maxHops) {
    this.startNode = startNode;
    hops = 0;
    nodes = new int[maxHops];
    edges = new int[maxHops];
    reverse = new boolean[maxHops];
  }

  public void addHop(int node, int edge, boolean rev) {
    nodes[hops] = node;
    edges[hops] = edge;
    reverse[hops] = rev;
    hops++;
  }

  public boolean alreadyVisited(int node) {
    // We could try to be smart with some kind of binary search, or something, but while MAX_HOPS
    // is around 10 (and generally the number of actual hops is 5 or less), it seems like this
    // should be fast enough.
    if (node == startNode) {
      return true;
    }
    for (int i=0; i<hops; i++) {
      if (nodes[i] == node)
        return true;
    }
    return false;
  }

  public int[] getNodes() {
    int[] seenNodes = new int[hops];
    System.arraycopy(nodes, 0, seenNodes, 0, hops);
    return seenNodes;
  }

  public int[] getEdges() {
    int[] seenEdges = new int[hops];
    System.arraycopy(edges, 0, seenEdges, 0, hops);
    return seenEdges;
  }

  public boolean[] getReverse() {
    boolean[] seenReverse = new boolean[hops];
    System.arraycopy(reverse, 0, seenReverse, 0, hops);
    return seenReverse;
  }

  public int getStartNode() {
    return startNode;
  }

  public int getHops() {
    return hops;
  }

  public int getLastEdgeType() {
    if (hops == 0) return -1;
    return edges[hops - 1];
  }

  @Override
  public String toString() {
    String result = "" + startNode;
    for (int i = 0; i < hops; i ++) {
      result += "-";
      if (reverse[i]) {
        result += "_";
      }
      result += edges[i] + "->" + nodes[i];
    }
    return result;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(edges);
    result = prime * result + hops;
    result = prime * result + Arrays.hashCode(nodes);
    result = prime * result + Arrays.hashCode(reverse);
    result = prime * result + startNode;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Path other = (Path) obj;
    if (!Arrays.equals(edges, other.edges))
      return false;
    if (hops != other.hops)
      return false;
    if (!Arrays.equals(nodes, other.nodes))
      return false;
    if (!Arrays.equals(reverse, other.reverse))
      return false;
    if (startNode != other.startNode)
      return false;
    return true;
  }
}
