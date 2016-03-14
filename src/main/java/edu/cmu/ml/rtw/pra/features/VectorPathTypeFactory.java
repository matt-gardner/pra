package edu.cmu.ml.rtw.pra.features;

import java.util.Map;
import java.util.Random;

import com.google.common.annotations.VisibleForTesting;

import edu.cmu.ml.rtw.pra.graphs.Graph;
import com.mattg.util.Vector;

/**
 * Represents a path type as a sequence of (edge, vector) pairs.  We assume that each edge type has
 * a vector representation that captures some amount of its semantic meaning.  The vector is used
 * during feature computation to allow walks across semantically similar edge types, as defined by
 * an exponentiated dot product.
 */
public class VectorPathTypeFactory extends BaseEdgeSequencePathTypeFactory {

  private final VectorPathType emptyPathType = new VectorPathType();
  // We're wasting a little bit of memory by making this an array instead of a map, but we're
  // gaining in computation time, by avoiding autoboxing and hashing.  Also, I expect that the
  // array will be mostly full, in most uses of this PathTypeFactory.  It's just KB edges that
  // don't have vectors, generally, and the number of relations types is dominated by surface
  // edges, not KB edges.
  private Vector[] embeddings;
  private final double resetWeight;
  private final double spikiness;
  private final Graph graph;
  private final Map<Integer, Vector> embeddingsMap;

  public VectorPathTypeFactory(Graph graph,
                               Map<Integer, Vector> embeddingsMap,
                               double spikiness,
                               double resetWeight) {
    super(graph);
    this.resetWeight = Math.exp(spikiness * resetWeight);
    this.spikiness = spikiness;
    this.graph = graph;
    this.embeddingsMap = embeddingsMap;
    initializeEmbeddings();
  }

  public Graph getGraph() {
    return graph;
  }

  public Map<Integer, Vector> getEmbeddingsMap() {
    return embeddingsMap;
  }

  public double getResetWeight() {
    return resetWeight;
  }

  public double getSpikiness() {
    return spikiness;
  }

  public void initializeEmbeddings() {
    int maxIndex = graph.getNumEdgeTypes();
    embeddings = new Vector[maxIndex + 1];
    for (Map.Entry<Integer, Vector> entry : embeddingsMap.entrySet()) {
      embeddings[entry.getKey()] = entry.getValue();
    }
  }

  @Override
  protected BaseEdgeSequencePathType newInstance(int[] edgeTypes, boolean[] reverse) {
    return new VectorPathType(edgeTypes, reverse);
  }

  @Override
  public PathType emptyPathType() {
    return emptyPathType;
  }

  public Vector getEmbedding(int edgeType) {
    return embeddings[edgeType];
  }

  @VisibleForTesting
  protected class VectorPathType extends BaseEdgeSequencePathType {

    // In profiling, it looked like a whole lot of time was taken up just _accessing_ these fields
    // from the enclosing class.  So I added these fields in order to improve performance a bit.
    // This will increase memory usage a bit, but should decrease processing time, which is the
    // more pressing issue.
    private double _spikiness;
    private double _resetWeight;
    private Vector[] _embeddings;

    public VectorPathType(int[] edgeTypes, boolean[] reverse) {
      super(edgeTypes, reverse);
      _spikiness = spikiness;
      _resetWeight = resetWeight;
      _embeddings = embeddings;
    }

    private VectorPathType() {
      super(new int[0], new boolean[0]);
    }

    @Override
    public PathTypeVertexCache cacheVertexInformation(Vertex vertex, int hopNum) {
      if (hopNum >= numHops()) return null;

      Vector baseVector = _embeddings[edgeTypes()[hopNum]];
      // If we have no embeddings for the edge type corresponding to this hop num (for instance, if
      // it is the "alias" relation), then just return the edge type itself.
      if (baseVector == null) {
        return new VectorPathTypeVertexCache(edgeTypes()[hopNum]);
      }

      int[] vertexEdgeTypes;
      if (reverse()[hopNum]) {
        vertexEdgeTypes = vertex.getInEdgeTypes();
      } else {
        vertexEdgeTypes = vertex.getOutEdgeTypes();
      }
      // If there's only one possible edge type, and it matches the edge type we're supposed to be
      // walking on, don't bother messing with the reset probability, just take it.
      if (vertexEdgeTypes.length == 1 && vertexEdgeTypes[0] == edgeTypes()[hopNum]) {
        return new VectorPathTypeVertexCache(edgeTypes()[hopNum]);
      }
      double[] weights = new double[vertexEdgeTypes.length];
      double totalWeight = 0.0;
      for (int i = 0; i < vertexEdgeTypes.length; i++) {
        Vector edgeVector = _embeddings[vertexEdgeTypes[i]];
        if (edgeVector == null) {
          weights[i] = 0.0;
          continue;
        }
        double dotProduct = baseVector.dotProduct(edgeVector);
        double weight = Math.exp(_spikiness * dotProduct);
        weights[i] = weight;
        totalWeight += weight;
      }
      totalWeight += _resetWeight;
      return new VectorPathTypeVertexCache(vertexEdgeTypes, weights, totalWeight);
    }

    @Override
    public int getNextEdgeType(int hopNum, Vertex vertex, Random random, PathTypeVertexCache _cache) {
      VectorPathTypeVertexCache cache = (VectorPathTypeVertexCache) _cache;
      if (cache.isDeltaDistribution()) return cache.deltaEdgeType;

      double randomWeight = random.nextDouble() * cache.totalWeight;
      for (int i = 0; i < cache.weights.length; i++) {
        if (randomWeight < cache.weights[i]) {
          return cache.edgeTypes[i];
        }
        randomWeight -= cache.weights[i];
      }
      // This corresponds to the reset weight that we added, in case all of the individual weights
      // were very low.  So return -1 to indicate that we should just reset the walk.
      return -1;
    }
  }
}
