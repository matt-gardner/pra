package edu.cmu.ml.rtw.pra;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.util.IdCount;
import edu.cmu.graphchi.util.IntegerBuffer;
import edu.cmu.graphchi.walks.distributions.DiscreteDistribution;
import edu.cmu.graphchi.walks.distributions.TwoKeyCompanion;

import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.ml.rtw.util.Dictionary;
import edu.cmu.ml.rtw.util.Pair;

public class PathFinderCompanion extends TwoKeyCompanion {
    private VertexIdTranslate translate;
    private int[] sourceVertexIds;
    private Dictionary pathDict;
    private PathTypePolicy policy;

    /**
     * Creates the TwoKeyCompanion object
     * @param numThreads number of worker threads (4 is common)
     * @param maxMemoryBytes maximum amount of memory to use for storing the distributions
     */
    public PathFinderCompanion(int numThreads,
                               long maxMemoryBytes,
                               VertexIdTranslate translate,
                               Dictionary pathDict,
                               PathTypePolicy policy) throws RemoteException {
        super(numThreads, maxMemoryBytes);
        this.translate = translate;
        this.pathDict = pathDict;
        this.policy = policy;
    }

    public void setSources(int[] sourceVertexIds) {
        this.sourceVertexIds = sourceVertexIds;
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

    @Override
    public void outputDistributions(String outputFile) throws RemoteException {
    }

    public Map<String, Integer> getPathCounts(List<Integer> sources, List<Integer> targets) {
        logger.info("Waiting for finish");
        waitForFinish();
        logger.info("Getting paths");
        for (Integer firstKey : buffers.keySet()) {
            ConcurrentHashMap<Integer, IntegerBuffer> map = buffers.get(firstKey);
            for (Integer secondKey : map.keySet()) {
                drainBuffer(firstKey, secondKey);
            }
        }
        HashSet<Integer> sourcesSet = new HashSet<Integer>(sources);
        HashSet<Integer> targetsSet = new HashSet<Integer>(targets);
        HashMap<String, Integer> pathCounts = new HashMap<String, Integer>();
        // TODO: allow acceptance of all path types from source nodes to any target node, and fix
        // this for the case when you only want paired target paths
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
                        incrementCounts(pathCounts, map.get(correspondingSource), "");
                    }
                } else if (policy == PathTypePolicy.EVERYTHING) {
                    for (Integer source : sourcesInMap) {
                        incrementCounts(pathCounts, map.get(source), "");
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
                        incrementCounts(pathCounts, "-", map.get(correspondingTarget));
                    }
                } else if (policy == PathTypePolicy.EVERYTHING) {
                    for (Integer target : targetsInMap) {
                        incrementCounts(pathCounts, "-", map.get(target));
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
    private void incrementCounts(HashMap<String, Integer> pathCounts, String sourcePath,
            DiscreteDistribution targetToInter) {
        for (IdCount vc : targetToInter.getTop(5)) {
            String pathType = pathDict.getString(vc.id);
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

    private void incrementCounts(HashMap<String, Integer> pathCounts,
            DiscreteDistribution sourceToInter, String targetPath) {
        for (IdCount vc : sourceToInter.getTop(5)) {
            String pathType = pathDict.getString(vc.id);
            int pathCount = vc.count;
            if (policy == PathTypePolicy.PAIRED_ONLY) {
                pathCount *= pathCount;
            } else if (policy == PathTypePolicy.EVERYTHING) {
                pathCount *= pathCount * pathCount;
            }
            incrementCounts(pathCounts, pathType, targetPath, pathCount);
        }
    }

    // This method, which calls the two helper methods below is for paths that have an intermediate
    // node.  We combine the path counts by multiplying them.
    private void incrementCounts(HashMap<String, Integer> pathCounts,
            DiscreteDistribution sourceToInter, DiscreteDistribution targetToInter) {
        for (IdCount vc : sourceToInter.getTop(5)) {
            String pathType = pathDict.getString(vc.id);
            int pathCount = vc.count;
            incrementCounts(pathCounts, pathType, targetToInter, pathCount);
        }
    }

    private void incrementCounts(HashMap<String, Integer> pathCounts, String sourcePath,
            DiscreteDistribution targetToInter, int count) {
        for (IdCount vc : targetToInter.getTop(5)) {
            String pathType = pathDict.getString(vc.id);
            int pathCount = vc.count;
            incrementCounts(pathCounts, sourcePath, pathType, count * pathCount);
        }
    }

    private void incrementCounts(HashMap<String, Integer> pathCounts, String sourcePath,
            String targetPath, int count) {
        String finalPath = combinePaths(sourcePath, targetPath);
        Integer prevCount = pathCounts.get(finalPath);
        if (prevCount == null) {
            pathCounts.put(finalPath, count);
        } else {
            pathCounts.put(finalPath, prevCount + count);
        }
    }

    private String combinePaths(String sourcePath, String targetPath) {
        if (targetPath.equals("-")) {
            return sourcePath;
        }
        String combined = sourcePath;
        String[] parts = targetPath.split("-");
        for (int i=parts.length-1; i>=0; i--) {
            String edge = parts[i];
            if (edge.length() == 0) {
                continue;
            }
            if (edge.charAt(0) == '_') {
                edge = edge.substring(1);
            } else {
                edge = "_" + edge;
            }
            combined += edge + "-";
        }
        return combined;
    }

    /**
     * Instead of returning a top list of paths, just return a feature matrix containing
     * everything that was found in the walk.  Where PathFollowerCompanion computes a few specific
     * columns of the feature matrix, this computes all (observed) columns of a few specific rows
     * of the matrix.
     */
    public List<MatrixRow> getFeatureMatrix() {
        logger.info("Waiting for execution to finish");
        waitForFinish();
        for (Integer firstKey : buffers.keySet()) {
            ConcurrentHashMap<Integer, IntegerBuffer> map = buffers.get(firstKey);
            for (Integer secondKey : map.keySet()) {
                drainBuffer(firstKey, secondKey);
            }
        }

        // Remember here that these are backwards - the _first_ key is the atVertex, the
        // _second_ key is the source node of the walk.  The path types show up as keys in the
        // DiscreteDistributions.  So we need to create a new map that goes from (source node, path
        // type) to (target node), normalize it, re-key (again) by (source node, target node), then
        // we can output a list of MatrixRows.

        // First go through the distributions and create a new map.
        Map<Integer, Map<Integer, Map<Integer, Integer>>> sourcePathTargetMap =
                new HashMap<Integer, Map<Integer, Map<Integer, Integer>>>();
        Map<Integer, Map<Integer, Integer>> sourcePathCounts =
                new HashMap<Integer, Map<Integer, Integer>>();
        for (Integer firstKey : distributions.keySet()) {
            ConcurrentHashMap<Integer, DiscreteDistribution> map = distributions.get(firstKey);
            for (Integer secondKey : map.keySet()) {
                DiscreteDistribution dist = map.get(secondKey);
                Map<Integer, Map<Integer, Integer>> pathTargetMap =
                        sourcePathTargetMap.get(secondKey);
                if (pathTargetMap == null) {
                    pathTargetMap = new HashMap<Integer, Map<Integer, Integer>>();
                    sourcePathTargetMap.put(secondKey, pathTargetMap);
                }
                Map<Integer, Integer> pathCounts = sourcePathCounts.get(secondKey);
                if (pathCounts == null) {
                    pathCounts = new HashMap<Integer, Integer>();
                    sourcePathCounts.put(secondKey, pathCounts);
                }
                for (IdCount ic : dist.getTop(dist.size())) {
                    int pathType = ic.id;
                    int count = ic.count;
                    Map<Integer, Integer> targetMap = pathTargetMap.get(pathType);
                    if (targetMap == null) {
                        targetMap = new HashMap<Integer, Integer>();
                        pathTargetMap.put(pathType, targetMap);
                    }
                    // The number of keys hasn't changed, so a (target, source, path) triple should
                    // be unique even as a (source, path, target) triple.  So we don't try to
                    // increment anything here, we just input a count.
                    targetMap.put(firstKey, count);
                    // But we can keep totals for the (source, path) tuple, for easier normalizing
                    // later.
                    Integer prevCount = pathCounts.get(pathType);
                    if (prevCount == null) {
                        prevCount = 0;
                    }
                    pathCounts.put(pathType, prevCount + count);
                }
            }
        }

        // Now we re-key by (source node, target node), and normalize the counts.
        Map<Pair<Integer, Integer>, List<Pair<Integer, Double>>> features =
            new HashMap<Pair<Integer, Integer>, List<Pair<Integer, Double>>>();
        for (Integer sourceNode : sourcePathTargetMap.keySet()) {
            Map<Integer, Map<Integer, Integer>> pathTargetMap = sourcePathTargetMap.get(sourceNode);
            for (Integer pathType : pathTargetMap.keySet()) {
                Map<Integer, Integer> targetMap = pathTargetMap.get(pathType);
                double total = sourcePathCounts.get(sourceNode).get(pathType).doubleValue();
                for (Integer targetNode : targetMap.keySet()) {
                    Integer count = targetMap.get(targetNode);
                    double percent = count / total;
                    Pair<Integer, Integer> nodePair =
                            new Pair<Integer, Integer>(sourceNode, targetNode);
                    Pair<Integer, Double> feature = new Pair<Integer, Double>(pathType, percent);
                    List<Pair<Integer, Double>> feature_list = features.get(nodePair);
                    if (feature_list == null) {
                        feature_list = new ArrayList<Pair<Integer, Double>>();
                        features.put(nodePair, feature_list);
                    }
                    feature_list.add(feature);
                }
            }
        }

        // Finally, loop through the (source, target) entries we just created and output a
        // MatrixRow.
        // TODO(matt): this chunk of code can probably be shared between this and
        // PathFollowerCompanion.  Also, make sure they are sorted.
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
            matrix.add(new MatrixRow(sourceNode, targetNode, pathTypes, values));
        }
        return matrix;
    }
}
