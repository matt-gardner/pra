package edu.cmu.ml.rtw.pra.features;

public class VectorPathTypeVertexCache implements PathTypeVertexCache {
  public final int[] edgeTypes;
  public final double[] weights;
  public final double totalWeight;
  public final int deltaEdgeType;

  public VectorPathTypeVertexCache(int[] edgeTypes, double[] weights, double totalWeight) {
    this.edgeTypes = edgeTypes;
    this.weights = weights;
    this.totalWeight = totalWeight;
    this.deltaEdgeType = -1;
  }

  public VectorPathTypeVertexCache(int deltaEdgeType) {
    this.deltaEdgeType = deltaEdgeType;
    this.edgeTypes = null;
    this.weights = null;
    this.totalWeight = -1;
  }

  public boolean isDeltaDistribution() {
    return deltaEdgeType != -1;
  }
}
