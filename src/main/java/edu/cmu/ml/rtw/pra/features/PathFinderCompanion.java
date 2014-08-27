package edu.cmu.ml.rtw.pra.features;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.util.IdCount;
import edu.cmu.graphchi.util.IntegerBuffer;
import edu.cmu.graphchi.walks.distributions.DiscreteDistribution;
import edu.cmu.graphchi.walks.distributions.TwoKeyCompanion;
import edu.cmu.ml.rtw.users.matt.util.Index;

public class PathFinderCompanion extends TwoKeyCompanion {
    private VertexIdTranslate translate;
    private int[] sourceVertexIds;
    private Index<PathType> pathDict;
    private PathTypePolicy policy;
    private PathTypeFactory pathTypeFactory;

    /**
     * Creates the TwoKeyCompanion object
     * @param numThreads number of worker threads (4 is common)
     * @param maxMemoryBytes maximum amount of memory to use for storing the distributions
     */
    public PathFinderCompanion(int numThreads,
                               long maxMemoryBytes,
                               VertexIdTranslate translate,
                               Index<PathType> pathDict,
                               PathTypeFactory pathTypeFactory,
                               PathTypePolicy policy) throws RemoteException {
        super(numThreads, maxMemoryBytes);
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
        return translate.backward(sourceVertexIds[PathFinder.staticSourceIdx(walk)]);
    }

    protected int getValue(long walk, int atVertex) {
        return PathFinder.Manager.pathType(walk);
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
        HashSet<Integer> sourcesSet = new HashSet<Integer>(sources);
        HashSet<Integer> targetsSet = new HashSet<Integer>(targets);
        Map<PathType, Integer> pathCounts = new HashMap<PathType, Integer>();
        for (Integer firstKey : distributions.keySet()) {
            ConcurrentHashMap<Integer, DiscreteDistribution> map = distributions.get(firstKey);
            Set<Integer> secondKeys = map.keySet();
            Set<Integer> sourcesInMap = new HashSet<Integer>(secondKeys);
            sourcesInMap.retainAll(sourcesSet);
            Set<Integer> targetsInMap = new HashSet<Integer>(secondKeys);
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
