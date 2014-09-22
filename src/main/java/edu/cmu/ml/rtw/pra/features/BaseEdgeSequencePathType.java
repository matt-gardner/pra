package edu.cmu.ml.rtw.pra.features;

import java.util.Arrays;
import java.util.Random;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;

public abstract class BaseEdgeSequencePathType implements PathType {
  protected final int[] edgeTypes;
  protected final boolean[] reverse;
  protected final int numHops;

  public BaseEdgeSequencePathType(int[] edgeTypes, boolean[] reverse) {
    this.edgeTypes = edgeTypes;
    this.reverse = reverse;
    numHops = edgeTypes.length;
  }

  @Override
  public int recommendedIters() {
    // The +1 here is because we need an iteration for each node, not for each edge
    return numHops + 1;
  }

  @Override
  public boolean isLastHop(int hopNum) {
    return hopNum == numHops - 1;
  }

  @Override
  public int nextHop(int hopNum,
                     int sourceVertex,
                     Vertex currentVertex,
                     Random random,
                     EdgeExcluder edgeExcluder,
                     PathTypeVertexCache cache) {
    if (hopNum >= numHops) {
      return -1;
    }
    int[] possibleHops;
    int edgeType = getNextEdgeType(hopNum, currentVertex, random, cache);
    boolean isReverse = reverse[hopNum];
    possibleHops = currentVertex.getPossibleNodes(edgeType, isReverse);
    if (possibleHops == null) {
      return -1;
    }
    int nextVertex = possibleHops[random.nextInt(possibleHops.length)];
    if (edgeExcluder.shouldExcludeEdge(sourceVertex,
                                       nextVertex,
                                       currentVertex,
                                       edgeType)) {
      return -1;
    }
    return nextVertex;
  }

  protected abstract int getNextEdgeType(int hopNum,
                                         Vertex vertex,
                                         Random random,
                                         PathTypeVertexCache cache);

  @Override
  public String encodeAsString() {
    return stringDescription(null);
  }

  @Override
  public String encodeAsHumanReadableString(Dictionary edgeDict) {
    return stringDescription(edgeDict);
  }

  private String stringDescription(Dictionary edgeDict) {
    StringBuilder builder = new StringBuilder();
    builder.append("-");
    for (int i = 0; i < numHops; i++) {
      if (reverse[i]) {
        builder.append("_");
      }
      if (edgeDict == null) {
        builder.append(edgeTypes[i]);
      } else {
        builder.append(edgeDict.getString(edgeTypes[i]));
      }
      builder.append("-");
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return encodeAsString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + numHops;
    result = prime * result + Arrays.hashCode(edgeTypes);
    result = prime * result + Arrays.hashCode(reverse);
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
    BaseEdgeSequencePathType other = (BaseEdgeSequencePathType) obj;
    if (numHops != other.numHops)
      return false;
    if (!Arrays.equals(edgeTypes, other.edgeTypes))
      return false;
    if (!Arrays.equals(reverse, other.reverse))
      return false;
    return true;
  }
}
