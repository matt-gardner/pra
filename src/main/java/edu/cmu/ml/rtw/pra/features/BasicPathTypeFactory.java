package edu.cmu.ml.rtw.pra.features;

import edu.cmu.ml.rtw.pra.graphs.Graph;

import java.util.Random;

/**
 * Represents PathTypes simply as sequences of edges.
 */
public class BasicPathTypeFactory extends BaseEdgeSequencePathTypeFactory {

  private final BasicPathType emptyPathType = new BasicPathType();

  public BasicPathTypeFactory(Graph graph) {
    super(graph);
  }

  @Override
  public PathType emptyPathType() {
    return emptyPathType;
  }

  @Override
  protected BaseEdgeSequencePathType newInstance(int[] edgeTypes, boolean[] reverse) {
    return new BasicPathType(edgeTypes, reverse);
  }

  public static class BasicPathType extends BaseEdgeSequencePathType {
    public BasicPathType(int[] edgeTypes, boolean[] reverse) {
      super(edgeTypes, reverse);
    }

    @Override
    public PathTypeVertexCache cacheVertexInformation(Vertex vertex, int hopNum) {
      return null;
    }

    @Override
    public int getNextEdgeType(int hopNum, Vertex vertex, Random random, PathTypeVertexCache cache) {
      return edgeTypes()[hopNum];
    }

    private BasicPathType() {
      super(new int[0], new boolean[0]);
    }
  }
}
