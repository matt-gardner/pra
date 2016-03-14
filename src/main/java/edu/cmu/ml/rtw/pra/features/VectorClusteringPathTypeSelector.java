package edu.cmu.ml.rtw.pra.features;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory;
import com.mattg.util.MapUtil;
import com.mattg.util.Pair;
import com.mattg.util.Vector;

public class VectorClusteringPathTypeSelector implements PathTypeSelector {

  private final VectorPathTypeFactory factory;
  private final double similarityThreshold;
  private int nextClusterIndex;
  public final static String CLUSTER_SEPARATOR = " C+C ";

  public VectorClusteringPathTypeSelector(VectorPathTypeFactory factory, double similarityThreshold) {
    this.factory = factory;
    this.similarityThreshold = similarityThreshold;
    nextClusterIndex = 1;
  }

  public double getSimilarityThreshold() {
    return similarityThreshold;
  }

  @Override
  public List<PathType> selectPathTypes(Map<PathType, Integer> pathCounts, int numPathsToKeep) {
    Map<PathType, Integer> clusteredPathTypes = Maps.newHashMap();
    for (List<Pair<PathType, Integer>> pathTypeGroup : groupBySignature(pathCounts)) {
      List<Pair<PathType, Integer>> clustered = clusterPathTypes(pathTypeGroup);
      for (Pair<PathType, Integer> pathTypeCount : clustered) {
        clusteredPathTypes.put(pathTypeCount.getLeft(), pathTypeCount.getRight());
      }
    }
    return MapUtil.getTopKeys(clusteredPathTypes, numPathsToKeep);
  }

  @VisibleForTesting
  protected Collection<List<Pair<PathType, Integer>>> groupBySignature(
      Map<PathType, Integer> pathCounts) {
    Map<String, List<Pair<PathType, Integer>>> grouped = Maps.newHashMap();
    for (Map.Entry<PathType, Integer> entry : pathCounts.entrySet()) {
      String signature = getSignature((VectorPathTypeFactory.VectorPathType) entry.getKey());
      Pair<PathType, Integer> pair = new Pair<PathType, Integer>(entry.getKey(), entry.getValue());
      MapUtil.addValueToKeyList(grouped, signature, pair);
    }
    return grouped.values();
  }

  @VisibleForTesting
  protected String getSignature(VectorPathTypeFactory.VectorPathType pathType) {
    String signature = "-";
    for (int i = 0; i < pathType.getEdgeTypes().length; i++) {
      boolean reverse = pathType.getReverse()[i];
      if (reverse) {
        signature += "_";
      }
      int edgeType = pathType.getEdgeTypes()[i];
      boolean hasVector = factory.getEmbedding(edgeType) != null;
      if (hasVector) {
        signature += "v-";
      } else {
        signature += edgeType + "-";
      }
    }
    return signature;
  }

  /**
   * Does agglomerative clustering on the vectors found in each path type, to reduce the number of
   * path types kept.
   *
   * This is a lot slower than it could be.  That might not matter, really, because this is just
   * done once per relation, after GraphChi has finished.
   */
  @VisibleForTesting
  protected List<Pair<PathType, Integer>> clusterPathTypes(List<Pair<PathType, Integer>> pathTypes) {
    List<Pair<PathType, Integer>> clustered = Lists.newArrayList(pathTypes);
    while (true) {
      double maxSimilarity = similarityThreshold;
      Pair<Pair<PathType, Integer>, Pair<PathType, Integer>> mostSimilarPair = null;
      for (int i = 0; i < clustered.size(); i++) {
        for (int j = i + 1; j < clustered.size(); j++) {
          Vector firstVector = getVectorFromPathType(clustered.get(i).getLeft());
          Vector secondVector = getVectorFromPathType(clustered.get(j).getLeft());
          double similarity = firstVector.dotProduct(secondVector);
          if (similarity > maxSimilarity) {
            maxSimilarity = similarity;
            mostSimilarPair = new Pair<Pair<PathType, Integer>, Pair<PathType, Integer>>(
                clustered.get(i), clustered.get(j));
          }
        }
      }
      if (mostSimilarPair == null) {
        break;
      }
      clustered.remove(mostSimilarPair.getLeft());
      clustered.remove(mostSimilarPair.getRight());
      Pair<PathType, Integer> combined = combinePathTypes(mostSimilarPair.getLeft(),
                                                          mostSimilarPair.getRight());
      clustered.add(combined);
    }
    return clustered;
  }

  @VisibleForTesting
  protected Vector getVectorFromPathType(PathType pathType_) {
    VectorPathTypeFactory.VectorPathType pathType = (VectorPathTypeFactory.VectorPathType) pathType_;
    Vector concatenatedVector = null;
    for (int i = 0; i < pathType.getEdgeTypes().length; i++) {
      Vector edgeVector = factory.getEmbedding(pathType.getEdgeTypes()[i]);
      if (edgeVector != null) {
        if (concatenatedVector == null) {
          concatenatedVector = edgeVector;
        } else {
          concatenatedVector = concatenatedVector.concatenate(edgeVector);
        }
      }
    }
    return concatenatedVector;
  }

  @VisibleForTesting
  protected Pair<PathType, Integer> combinePathTypes(Pair<PathType, Integer> first,
                                                     Pair<PathType, Integer> second) {
    VectorPathTypeFactory.VectorPathType firstPathType = (VectorPathTypeFactory.VectorPathType) first.getLeft();
    VectorPathTypeFactory.VectorPathType secondPathType = (VectorPathTypeFactory.VectorPathType) second.getLeft();
    int numEdgeTypes = firstPathType.getEdgeTypes().length;
    int[] newEdgeTypes = new int[numEdgeTypes];
    boolean[] newReverse = new boolean[numEdgeTypes];
    for (int i = 0; i < numEdgeTypes; i++) {
      newReverse[i] = firstPathType.getReverse()[i];
      Vector firstVector = factory.getEmbedding(firstPathType.getEdgeTypes()[i]);
      if (firstVector == null) {
        // We don't need to combine the vectors, so we know from the signature that these two path
        // types have the same edge here.  So just add it and move on.
        newEdgeTypes[i] = firstPathType.getEdgeTypes()[i];
        continue;
      }
      // We need to combine the vectors from the two path types here into a clustered vector,
      // weighted by their counts.  Weighting by count is a poor man's version of actually keeping
      // track of how many vectors are represented by a current cluster mean, in order to avoid a
      // lot of extra bookkeeping.  And it even seems like a reasonable thing to do apart from
      // bookkeeping considerations.
      String firstName = factory.getGraph().getEdgeName(firstPathType.getEdgeTypes()[i]);
      String secondName = factory.getGraph().getEdgeName(secondPathType.getEdgeTypes()[i]);
      String clusterEdgeName = firstName + CLUSTER_SEPARATOR + secondName;
      int clusterEdgeIndex = factory.getGraph().getEdgeIndex(clusterEdgeName);
      newEdgeTypes[i] = clusterEdgeIndex;
      Vector secondVector = factory.getEmbedding(secondPathType.getEdgeTypes()[i]);
      int firstCount = first.getRight();
      int secondCount = second.getRight();
      Vector combinedVector = firstVector.multiply(firstCount).add(secondVector.multiply(secondCount));
      combinedVector.normalize();
      factory.getEmbeddingsMap().put(clusterEdgeIndex, combinedVector);
    }
    // Also really slow, as this creates a new array and iterates over all stored vectors.
    // But, it'll do for now.
    factory.initializeEmbeddings();
    return new Pair<PathType, Integer>(factory.newInstance(newEdgeTypes, newReverse),
                                       first.getRight() + second.getRight());
  }
}
