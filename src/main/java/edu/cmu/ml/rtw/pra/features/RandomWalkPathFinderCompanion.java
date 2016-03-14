package edu.cmu.ml.rtw.pra.features;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.util.IdCount;
import edu.cmu.graphchi.util.IntegerBuffer;
import edu.cmu.graphchi.walks.distributions.DiscreteDistribution;
import edu.cmu.graphchi.walks.distributions.TwoKeyCompanion;
import edu.cmu.ml.rtw.pra.data.NodePairInstance;
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk;
import com.mattg.util.Index;
import com.mattg.util.MapUtil;
import com.mattg.util.Pair;

public class RandomWalkPathFinderCompanion extends TwoKeyCompanion {
  private VertexIdTranslate translate;
  private int[] sourceVertexIds;
  private Index<PathType> pathDict;
  private PathTypePolicy policy;
  private PathTypeFactory pathTypeFactory;
  private final GraphOnDisk graph;

  /**
   * Creates the TwoKeyCompanion object
   * @param numThreads number of worker threads (4 is common)
   * @param maxMemoryBytes maximum amount of memory to use for storing the distributions
   */
  public RandomWalkPathFinderCompanion(GraphOnDisk graph,
                                       int numThreads,
                                       long maxMemoryBytes,
                                       VertexIdTranslate translate,
                                       Index<PathType> pathDict,
                                       PathTypeFactory pathTypeFactory,
                                       PathTypePolicy policy) throws RemoteException {
    super(numThreads, maxMemoryBytes);
    this.graph = graph;
    this.translate = translate;
    this.pathDict = pathDict;
    this.pathTypeFactory = pathTypeFactory;
    this.policy = policy;
  }

  public void setSources(int[] sourceVertexIds) {
    this.sourceVertexIds = sourceVertexIds;
  }

  public void setPolicy(PathTypePolicy policy) {
    this.policy = policy;
  }

  protected int getFirstKey(long walk, int atVertex) {
    return translate.backward(atVertex);
  }

  protected int getSecondKey(long walk, int atVertex) {
    return translate.backward(sourceVertexIds[RandomWalkPathFinder.staticSourceIdx(walk)]);
  }

  protected int getValue(long walk, int atVertex) {
    return RandomWalkPathFinder.Manager.pathType(walk);
  }

  @VisibleForTesting
  protected void setDistributions(
      ConcurrentHashMap<Integer,
      ConcurrentHashMap<Integer, DiscreteDistribution>> distributions) {
    this.distributions = distributions;
  }

  @Override
  public void outputDistributions(String outputFile) throws RemoteException {
  }

  private void assureReady() {
    waitForFinish();
    for (Integer firstKey : buffers.keySet()) {
      ConcurrentHashMap<Integer, IntegerBuffer> map = buffers.get(firstKey);
      for (Integer secondKey : map.keySet()) {
        drainBuffer(firstKey, secondKey);
      }
    }
  }

  public Map<PathType, Integer> getPathCounts(List<Integer> sources, List<Integer> targets) {
    logger.info("Waiting for finish");
    assureReady();
    logger.info("Getting paths");
    HashSet<Integer> sourcesSet = Sets.newHashSet(sources);
    HashSet<Integer> targetsSet = Sets.newHashSet(targets);
    Map<PathType, Integer> pathCounts = Maps.newHashMap();
    for (Integer firstKey : distributions.keySet()) {
      ConcurrentHashMap<Integer, DiscreteDistribution> map = distributions.get(firstKey);
      Set<Integer> secondKeys = map.keySet();
      Set<Integer> sourcesInMap = Sets.newHashSet(secondKeys);
      sourcesInMap.retainAll(sourcesSet);
      Set<Integer> targetsInMap = Sets.newHashSet(secondKeys);
      targetsInMap.retainAll(targetsSet);
      // Remember here that these are backwards - the _first_ key is the atVertex, the
      // _second_ key is the source node of the walk.

      // First, did we end up at a target node, coming from a source node?
      if (targetsSet.contains(firstKey)) {
        if (policy == PathTypePolicy.PAIRED_ONLY) {
          int i = targets.indexOf(firstKey);
          int correspondingSource = sources.get(i);
          if (map.containsKey(correspondingSource)) {
            incrementCounts(pathCounts,
                            map.get(correspondingSource),
                            pathTypeFactory.emptyPathType());
          }
        } else if (policy == PathTypePolicy.EVERYTHING) {
          for (Integer source : sourcesInMap) {
            incrementCounts(pathCounts,
                            map.get(source),
                            pathTypeFactory.emptyPathType());
          }
        } else {
          throw new RuntimeException("Unknown path type policy: " + policy);
        }
      }
      // Second, did we end at a source node, coming from a target node?
      if (sourcesSet.contains(firstKey)) {
        if (policy == PathTypePolicy.PAIRED_ONLY) {
          int i = sources.indexOf(firstKey);
          int correspondingTarget = targets.get(i);
          if (map.containsKey(correspondingTarget)) {
            incrementCounts(pathCounts,
                            pathTypeFactory.emptyPathType(),
                            map.get(correspondingTarget));
          }
        } else if (policy == PathTypePolicy.EVERYTHING) {
          for (Integer target : targetsInMap) {
            incrementCounts(pathCounts,
                            pathTypeFactory.emptyPathType(),
                            map.get(target));
          }
        } else {
          throw new RuntimeException("Unknown path type policy: " + policy);
        }
      }
      // Lastly, we see if for any (source, target) pair, a walk from both of them reached
      // this intermediate node.
      for (int i=0; i<sources.size(); i++) {
        if (policy == PathTypePolicy.PAIRED_ONLY) {
          int source = sources.get(i);
          int target = targets.get(i);
          if (secondKeys.contains(source) && secondKeys.contains(target)) {
            DiscreteDistribution sourceToInter = map.get(source);
            DiscreteDistribution targetToInter = map.get(target);
            incrementCounts(pathCounts, sourceToInter, targetToInter);
          }
        } else if (policy == PathTypePolicy.EVERYTHING) {
          // It takes too much time and memory to do this exhaustively, so we'll just
          // sample the first 10 of each.
          int s = 0;
          for (Integer source : sourcesInMap) {
            if (++s >= 10) break;
            int t = 0;
            for (Integer target : targetsInMap) {
              if (++t >= 10) break;
              if (sourcesSet.contains(source) && targetsSet.contains(target)) {
                DiscreteDistribution sourceToInter = map.get(source);
                DiscreteDistribution targetToInter = map.get(target);
                incrementCounts(pathCounts, sourceToInter, targetToInter);
              }
            }
          }
        } else {
          throw new RuntimeException("Unknown path type policy: " + policy);
        }
      }
    }
    return pathCounts;
  }

  /**
   * This is very similar to getPathCounts, with two major differences.  First, by definition
   * PathTypePolicy is PAIRED_ONLY, so we don't need to check the policy.  Second, every time we
   * increment counts, we do it on a (source, target)-pair-specific map, instead of a global path
   * type map.  This way, we can return a set of path types for each (source, target) pair.  It's
   * unfortunate that there's so much code duplication.  It's possible that it could be cleaned up,
   * but without lambda expressions it'd still be kind of ugly.
   */
  public Map<NodePairInstance, Map<PathType, Integer>> getPathCountMap(
      List<NodePairInstance> instances) {
    logger.info("Waiting for finish");
    assureReady();
    logger.info("Getting paths");

    List<Integer> sources = Lists.newArrayList();
    List<Integer> targets = Lists.newArrayList();
    Map<Pair<Integer, Integer>, NodePairInstance> instanceMap = Maps.newHashMap();
    for (NodePairInstance instance : instances) {
      sources.add(instance.source());
      targets.add(instance.target());
      instanceMap.put(Pair.makePair(instance.source(), instance.target()), instance);
    }
    HashSet<Integer> sourcesSet = Sets.newHashSet(sources);
    HashSet<Integer> targetsSet = Sets.newHashSet(targets);

    Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCountMap = Maps.newHashMap();
    for (Integer firstKey : distributions.keySet()) {
      ConcurrentHashMap<Integer, DiscreteDistribution> map = distributions.get(firstKey);
      Set<Integer> secondKeys = map.keySet();
      Set<Integer> sourcesInMap = Sets.newHashSet(secondKeys);
      sourcesInMap.retainAll(sourcesSet);
      Set<Integer> targetsInMap = Sets.newHashSet(secondKeys);
      targetsInMap.retainAll(targetsSet);
      // Remember here that these are backwards - the _first_ key is the atVertex, the
      // _second_ key is the source node of the walk.

      // First, did we end up at a target node, coming from a source node?
      if (targetsSet.contains(firstKey)) {
        int i = targets.indexOf(firstKey);
        int correspondingSource = sources.get(i);
        if (map.containsKey(correspondingSource)) {
          Pair<Integer, Integer> sourceTargetPair =
              new Pair<Integer, Integer>(correspondingSource, firstKey);
          Map<PathType, Integer> newMap = Maps.newHashMap();
          incrementCounts(MapUtil.getWithDefaultAndAdd(pathCountMap, sourceTargetPair, newMap),
                          map.get(correspondingSource),
                          pathTypeFactory.emptyPathType());
        }
      }
      // Second, did we end at a source node, coming from a target node?
      if (sourcesSet.contains(firstKey)) {
        int i = sources.indexOf(firstKey);
        int correspondingTarget = targets.get(i);
        if (map.containsKey(correspondingTarget)) {
          Pair<Integer, Integer> sourceTargetPair =
              new Pair<Integer, Integer>(firstKey, correspondingTarget);
          Map<PathType, Integer> newMap = Maps.newHashMap();
          incrementCounts(MapUtil.getWithDefaultAndAdd(pathCountMap, sourceTargetPair, newMap),
                          pathTypeFactory.emptyPathType(),
                          map.get(correspondingTarget));
        }
      }
      // Lastly, we see if for any (source, target) pair, a walk from both of them reached
      // this intermediate node.
      for (int i=0; i<sources.size(); i++) {
        int source = sources.get(i);
        int target = targets.get(i);
        if (secondKeys.contains(source) && secondKeys.contains(target)) {
          DiscreteDistribution sourceToInter = map.get(source);
          DiscreteDistribution targetToInter = map.get(target);
          Pair<Integer, Integer> sourceTargetPair = new Pair<Integer, Integer>(source, target);
          Map<PathType, Integer> newMap = Maps.newHashMap();
          incrementCounts(MapUtil.getWithDefaultAndAdd(pathCountMap, sourceTargetPair, newMap),
                          sourceToInter,
                          targetToInter);
        }
      }
    }
    Map<NodePairInstance, Map<PathType, Integer>> instancePathCountMap = Maps.newHashMap();

    for (Map.Entry<Pair<Integer, Integer>, Map<PathType, Integer>> entry : pathCountMap.entrySet()) {
      NodePairInstance instance = instanceMap.get(entry.getKey());
      if (instance == null) {
        instance = new NodePairInstance(entry.getKey().getLeft(), entry.getKey().getRight(), false, graph);
      }
      instancePathCountMap.put(instance, entry.getValue());
    }
    return instancePathCountMap;
  }

  /**
   * Also very similar to getPathCounts, but more similar to getPathCountMap.  While
   * getPathCountMap returns a (path type, count) map for every (source, target) pair, this returns
   * a (path type, set(node, node)) map.  That is, for every (source, target) pair, we just return
   * _all_ walks that started from either the source or the target, including those that ended up
   * at intermediate nodes.  So in the set(node, node), the first node in the pair will always be
   * either the source or the target, and the second node in the pair is unconstrained.
   * getPathCountMap, by contrast, constrains the first node to be the source, and the second node
   * to be the target, and returns a count.
   *
   * Note that this method still does path combining on intermediate nodes.
   */
  public Map<NodePairInstance, Map<PathType, Set<Pair<Integer, Integer>>>> getLocalSubgraphs(
      List<NodePairInstance> instances) {
    logger.info("Waiting for finish");
    assureReady();
    logger.info("Getting paths");
    List<Integer> sources = Lists.newArrayList();
    List<Integer> targets = Lists.newArrayList();
    for (NodePairInstance instance : instances) {
      sources.add(instance.source());
      targets.add(instance.target());
    }
    // First we just get the two-sided paths using the method above; no need to duplicate all of
    // that code.
    Map<NodePairInstance, Map<PathType, Integer>> pathCountMap = getPathCountMap(instances);

    // Now we go through all of the walk counts a second time, to get one-sided paths.  It might be
    // more efficient to do this at the same time, but for now this will do.
    Map<Integer, Map<PathType, Set<Pair<Integer, Integer>>>> oneSidedPaths = Maps.newHashMap();
    for (Integer intermediateNode : distributions.keySet()) {
      ConcurrentHashMap<Integer, DiscreteDistribution> map = distributions.get(intermediateNode);
      for (int originNode : map.keySet()) {
        DiscreteDistribution dist = map.get(originNode);
        for (IdCount vc : dist.getTop(10)) {
          PathType pathType = pathDict.getKey(vc.id);
          Map<PathType, Set<Pair<Integer, Integer>>> subgraph = oneSidedPaths.get(originNode);
          if (subgraph == null) {
            subgraph = Maps.newHashMap();
            oneSidedPaths.put(originNode, subgraph);
          }
          MapUtil.addValueToKeySet(subgraph, pathType, Pair.makePair(originNode, intermediateNode));
        }
      }
    }

    Map<NodePairInstance, Map<PathType, Set<Pair<Integer, Integer>>>> localSubgraphs = Maps.newHashMap();

    // And finally, we merge the two maps.
    for (NodePairInstance instance : instances) {
      int source = instance.source();
      int target = instance.target();
      Pair<Integer, Integer> sourceTarget = Pair.makePair(source, target);
      Map<PathType, Set<Pair<Integer, Integer>>> subgraph = Maps.newHashMap();
      Map<PathType, Set<Pair<Integer, Integer>>> oneSidedSource = oneSidedPaths.get(source);
      if (oneSidedSource != null) {
        for (Map.Entry<PathType, Set<Pair<Integer, Integer>>> entry : oneSidedSource.entrySet()) {
          for (Pair<Integer, Integer> pair : entry.getValue()) {
            if (source != pair.getLeft() && target != pair.getLeft()) {
              // I'm still not really sure why this happens, because it sure looks from the code
              // above that this should be impossible.  But it's happening, and it breaks some
              // assumptions that are made downstream.  So we just ignore these cases for now.
              // TODO(matt): figure out what is actually causing this and fix it.
              continue;
            }
            MapUtil.addValueToKeySet(subgraph, entry.getKey(), pair);
          }
        }
      }
      Map<PathType, Set<Pair<Integer, Integer>>> oneSidedTarget = oneSidedPaths.get(target);
      if (oneSidedTarget != null) {
        for (Map.Entry<PathType, Set<Pair<Integer, Integer>>> entry : oneSidedTarget.entrySet()) {
          for (Pair<Integer, Integer> pair : entry.getValue()) {
            if (source != pair.getLeft() && target != pair.getLeft()) {
              // I'm still not really sure why this happens, because it sure looks from the code
              // above that this should be impossible.  But it's happening, and it breaks some
              // assumptions that are made downstream.  So we just ignore these cases for now.
              // TODO(matt): figure out what is actually causing this and fix it.
              continue;
            }
            MapUtil.addValueToKeySet(subgraph, entry.getKey(), pair);
          }
        }
      }
      Map<PathType, Integer> twoSided = pathCountMap.get(instance);
      if (twoSided != null) {
        for (PathType pathType : twoSided.keySet()) {
          MapUtil.addValueToKeySet(subgraph, pathType, sourceTarget);
        }
      }
      localSubgraphs.put(instance, subgraph);
    }
    return localSubgraphs;
  }

  // These top two get called when we have a direct path from source to target.  We square the
  // path count in that case, to account for the effects of the multiplication of the
  // intermediate path counts below.
  @VisibleForTesting
  protected void incrementCounts(Map<PathType, Integer> pathCounts, PathType sourcePath,
                                 DiscreteDistribution targetToInter) {
    for (IdCount vc : targetToInter.getTop(5)) {
      PathType pathType = pathDict.getKey(vc.id);
      int pathCount = vc.count;
      if (policy == PathTypePolicy.PAIRED_ONLY) {
        pathCount *= pathCount;
      } else if (policy == PathTypePolicy.EVERYTHING) {
        // Here the problems of intermediate nodes are even worse, because we're looping
        // over all sources and targets at an intermediate node.
        pathCount *= pathCount * pathCount;
      }
      incrementCounts(pathCounts, sourcePath, pathType, pathCount);
    }
  }

  @VisibleForTesting
  protected void incrementCounts(Map<PathType, Integer> pathCounts,
                                 DiscreteDistribution sourceToInter, PathType targetPath) {
    for (IdCount vc : sourceToInter.getTop(5)) {
      PathType pathType = pathDict.getKey(vc.id);
      int pathCount = vc.count;
      if (policy == PathTypePolicy.PAIRED_ONLY) {
        pathCount *= pathCount;
      } else if (policy == PathTypePolicy.EVERYTHING) {
        pathCount *= pathCount * pathCount;
      }
      incrementCounts(pathCounts, pathType, targetPath, pathCount);
    }
  }

  // This method, which calls the two helper methods below, is for paths that have an intermediate
  // node.  We combine the path counts by multiplying them.
  @VisibleForTesting
  protected void incrementCounts(Map<PathType, Integer> pathCounts,
                                 DiscreteDistribution sourceToInter, DiscreteDistribution targetToInter) {
    for (IdCount vc : sourceToInter.getTop(5)) {
      PathType pathType = pathDict.getKey(vc.id);
      int pathCount = vc.count;
      incrementCounts(pathCounts, pathType, targetToInter, pathCount);
    }
  }

  @VisibleForTesting
  protected void incrementCounts(Map<PathType, Integer> pathCounts, PathType sourcePath,
                                 DiscreteDistribution targetToInter, int count) {
    for (IdCount vc : targetToInter.getTop(5)) {
      PathType pathType = pathDict.getKey(vc.id);
      int pathCount = vc.count;
      incrementCounts(pathCounts, sourcePath, pathType, count * pathCount);
    }
  }

  @VisibleForTesting
  protected void incrementCounts(Map<PathType, Integer> pathCounts, PathType sourcePath,
                                 PathType targetPath, int count) {
    PathType finalPath = pathTypeFactory.concatenatePathTypes(sourcePath, targetPath);
    Integer prevCount = pathCounts.get(finalPath);
    if (prevCount == null) {
      pathCounts.put(finalPath, count);
    } else {
      pathCounts.put(finalPath, prevCount + count);
    }
  }
}
