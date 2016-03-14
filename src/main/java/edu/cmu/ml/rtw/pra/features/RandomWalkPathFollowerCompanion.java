package edu.cmu.ml.rtw.pra.features;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.util.IdCount;
import edu.cmu.graphchi.util.IntegerBuffer;
import edu.cmu.graphchi.walks.distributions.DiscreteDistribution;
import edu.cmu.graphchi.walks.distributions.TwoKeyCompanion;
import edu.cmu.ml.rtw.pra.data.NodePairInstance;
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk;
import com.mattg.util.MapUtil;
import com.mattg.util.Pair;

public class RandomWalkPathFollowerCompanion extends TwoKeyCompanion {

  private MatrixRowPolicy acceptPolicy;
  private Set<Integer> allowedTargets;

  private VertexIdTranslate translate;
  private int[] sourceVertexIds;
  private PathType[] pathTypes;
  private final boolean normalizeWalkProbabilities;
  private final GraphOnDisk graph;

  /**
   * Creates the PathFollowerCompanion object
   */
  public RandomWalkPathFollowerCompanion(GraphOnDisk graph,
                                         int numThreads,
                                         long maxMemoryBytes,
                                         VertexIdTranslate translate,
                                         PathType[] pathTypes,
                                         MatrixRowPolicy acceptPolicy,
                                         Set<Integer> allowedTargets,
                                         boolean normalizeWalkProbabilities) throws RemoteException {
    super(numThreads, maxMemoryBytes);
    this.translate = translate;
    this.pathTypes = pathTypes;
    this.acceptPolicy = acceptPolicy;
    this.allowedTargets = allowedTargets;
    this.normalizeWalkProbabilities = normalizeWalkProbabilities;
    this.graph = graph;
  }

  @Override
  public void setSources(int[] sourceVertexIds) {
    this.sourceVertexIds = sourceVertexIds;
  }

  @Override
  protected int getFirstKey(long walk, int atVertex) {
    return translate.backward(sourceVertexIds[RandomWalkPathFollower.staticSourceIdx(walk)]);
  }

  @Override
  protected int getSecondKey(long walk, int atVertex) {
    return RandomWalkPathFollower.Manager.pathType(walk);
  }

  @Override
  protected int getValue(long walk, int atVertex) {
    return translate.backward(atVertex);
  }

  @Override
  protected boolean ignoreWalk(long walk) {
    /*
    int pathType = PathFollower.Manager.pathType(walk);
    int hopNum = PathFollower.Manager.hopNum(walk);
    if (hopNum != pathTypes[pathType].numHops) {
      return true;
    }
     */
    return false;
  }

  @VisibleForTesting
  protected void setAcceptPolicy(MatrixRowPolicy acceptPolicy) {
    this.acceptPolicy = acceptPolicy;
  }

  @VisibleForTesting
  protected MatrixRowPolicy getAcceptPolicy() {
    return acceptPolicy;
  }

  @VisibleForTesting
  protected boolean getNormalizeWalks() {
    return normalizeWalkProbabilities;
  }

  @VisibleForTesting
  protected void setAllowedTargets(Set<Integer> allowedTargets) {
    this.allowedTargets = allowedTargets;
  }

  @VisibleForTesting
  protected void setDistributions(
      ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, DiscreteDistribution>> distributions) {
    this.distributions = distributions;
  }

  @Override
  public void outputDistributions(String outputFile) throws RemoteException {
  }

  @VisibleForTesting
  protected boolean acceptableRow(int sourceNode,
                                  int targetNode,
                                  Set<Integer> sourceTargets,
                                  Set<Integer> allTargets) {
    if (acceptPolicy == MatrixRowPolicy.EVERYTHING) {
      return true;
    } else if (acceptPolicy == MatrixRowPolicy.ALL_TARGETS) {
      if (allowedTargets != null) {
        return allowedTargets.contains(targetNode);
      } else {
        return allTargets.contains(targetNode);
      }
    } else if (acceptPolicy == MatrixRowPolicy.PAIRED_TARGETS_ONLY) {
      return sourceTargets.contains(targetNode);
    }
    throw new RuntimeException("Accept policy not set to something recognizable: " + acceptPolicy);
  }

  public FeatureMatrix getFeatureMatrix(List<NodePairInstance> instances) {
    Set<Pair<Integer, Integer>> positiveSourceTargets = Sets.newHashSet();
    for (NodePairInstance instance : instances) {
      if (instance.isPositive()) {
        positiveSourceTargets.add(Pair.makePair(instance.source(), instance.target()));
      }
    }
    Map<Integer, Set<Integer>> sourcesMap = Maps.newHashMap();
    for (NodePairInstance instance : instances) {
      MapUtil.addValueToKeySet(sourcesMap, instance.source(), instance.target());
    }
    logger.info("Waiting for execution to finish");
    waitForFinish();
    for (Integer firstKey : buffers.keySet()) {
      ConcurrentHashMap<Integer, IntegerBuffer> map = buffers.get(firstKey);
      for (Integer secondKey : map.keySet()) {
        drainBuffer(firstKey, secondKey);
      }
    }
    HashSet<Integer> emptySet = Sets.newHashSet();
    Set<Integer> allTargets = new HashSet<Integer>();
    for (int source : sourcesMap.keySet()) {
      Set<Integer> targets = MapUtil.getWithDefault(sourcesMap, source, emptySet);
      for (int target : targets) {
        allTargets.add(target);
      }
    }

    Map<Pair<Integer, Integer>, List<Pair<Integer, Double>>> features =
        new HashMap<Pair<Integer, Integer>, List<Pair<Integer, Double>>>();
    // firstKey is the (already translated) source vertex, secondKey is the path type, and the
    // target vertices show up as keys in the DiscreteDistributions
    for (Integer firstKey : distributions.keySet()) {
      ConcurrentHashMap<Integer, DiscreteDistribution> map = distributions.get(firstKey);
      Set<Integer> sourceTargets = MapUtil.getWithDefault(sourcesMap, firstKey, emptySet);
      for (Integer secondKey : map.keySet()) {
        DiscreteDistribution dist = map.get(secondKey);
        double totalCount = (double) dist.totalCount();
        /* For debugging
        for (IdCount ic : dist.getTop(10)) {
          System.out.println(firstKey + " " + ic.id + " " + secondKey + " " + ic.count);
          System.out.println(acceptableRow(firstKey, ic.id, sourceTargets, allTargets));
        }
        System.out.println();
         */
        for (IdCount ic : dist.getTop(dist.size())) {
          int target = ic.id;
          if (!acceptableRow(firstKey, target, sourceTargets, allTargets)) continue;
          int count = ic.count;
          double percent = count;
          if (normalizeWalkProbabilities) percent /= totalCount;
          if (Double.isInfinite(percent)) {
            // Somehow this happens when there are lots of walks...  Not sure what's going on.
            // TODO(matt): look into this problem.
            continue;
          }
          Pair<Integer, Integer> nodePair = new Pair<Integer, Integer>(firstKey, target);
          Pair<Integer, Double> feature = new Pair<Integer, Double>(secondKey, percent);
          MapUtil.addValueToKeyList(features, nodePair, feature);
        }
      }
    }
    List<MatrixRow> matrix = new ArrayList<MatrixRow>();
    for (Pair<Integer, Integer> nodePair : features.keySet()) {
      int sourceNode = nodePair.getLeft();
      int targetNode = nodePair.getRight();
      List<Pair<Integer, Double>> feature_list = features.get(nodePair);
      int[] pathTypes = new int[feature_list.size()];
      double[] values = new double[feature_list.size()];
      for (int i=0; i<feature_list.size(); i++) {
        pathTypes[i] = feature_list.get(i).getLeft();
        values[i] = feature_list.get(i).getRight();
      }
      boolean isPositive = false;
      if (positiveSourceTargets.contains(nodePair)) {
        isPositive = true;
      }
      NodePairInstance instance = new NodePairInstance(sourceNode, targetNode, isPositive, graph);
      matrix.add(new MatrixRow(instance, pathTypes, values));
    }
    return new FeatureMatrix(matrix);
  }
}
