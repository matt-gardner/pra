package edu.cmu.ml.rtw.pra;

import edu.cmu.graphchi.*;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.walks.DrunkardContext;
import edu.cmu.graphchi.walks.DrunkardJob;
import edu.cmu.graphchi.walks.DrunkardMobEngine;
import edu.cmu.graphchi.walks.LongDrunkardContext;
import edu.cmu.graphchi.walks.LongDrunkardFactory;
import edu.cmu.graphchi.walks.LongWalkArray;
import edu.cmu.graphchi.walks.LongWalkManager;
import edu.cmu.graphchi.walks.WalkArray;
import edu.cmu.graphchi.walks.WalkManager;
import edu.cmu.graphchi.walks.WalkUpdateFunction;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds common path types between pairs of nodes in a given set, using DrunkardMobEngine.
 * @author Matt Gardner
 */
public class PathFollower implements WalkUpdateFunction<EmptyType, Integer> {

    private static Logger logger = ChiLogger.getLogger("path-follower");
    private DrunkardMobEngine<EmptyType, Integer>  drunkardMobEngine;
    private int numWalksPerPath;
    private int[] sourceIds;
    private int numPaths;
    private int numIters;
    private PathType[] pathTypes;
    private Map<Integer, Set<Integer>> sourcesMap;
    // These maps might be a little slow; I'll have to look at performance and see if it's worth it
    // to change this.
    private Map<Integer, Set<Integer>> unallowedWalks;
    private Set<Integer> unallowedEdges;
    private VertexIdTranslate vertexIdTranslate;
    private PathFollowerCompanion companion;
    private Object printLock = new Object();

    public PathFollower(String baseFilename, int numShards, Map<Integer, Set<Integer>> sourcesMap,
            Set<Integer> allowedTargets, Set<Integer> unallowedEdges, List<PathType> paths,
            int walksPerPath, MatrixRowPolicy companionAcceptPolicy) {
        this.numWalksPerPath = walksPerPath;
        this.unallowedEdges = unallowedEdges;
        this.sourcesMap = sourcesMap;
        try {
            this.drunkardMobEngine = new DrunkardMobEngine<EmptyType, Integer>(baseFilename,
                    numShards, new Factory());
            this.drunkardMobEngine.setEdataConverter(new IntConverter());
        } catch (IOException e) {
            throw new RuntimeException("IOException when creating DrunkardMobEngine");
        }
        vertexIdTranslate = this.drunkardMobEngine.getVertexIdTranslate();
        pathTypes = new PathType[paths.size()];
        numPaths = 0;
        numIters = 0;
        for (PathType p : paths) {
            pathTypes[numPaths++] = p;
            // The +1 here is because we need an iteration for each node, not for each edge
            numIters = Math.max(numIters, p.numHops+1);
        }
        unallowedWalks = new HashMap<Integer, Set<Integer>>();

        /* Configure walk sources. Note, GraphChi's internal ids are used. */
        List<Integer> sources = new ArrayList<Integer>();
        for (int source : sourcesMap.keySet()) {
            // The training data is a map going from a source to a set of known target instances.
            // We do walks starting from each source, following each of a given set of paths.
            int translatedSource = vertexIdTranslate.forward(source);
            sources.add(translatedSource);
            Set<Integer> targets = sourcesMap.get(source);
            Set<Integer> sourceUnallowed = unallowedWalks.get(translatedSource);
            if (sourceUnallowed == null) {
                sourceUnallowed = new HashSet<Integer>();
                unallowedWalks.put(translatedSource, sourceUnallowed);
            }
            if (targets == null) continue;
            for (int target : targets) {
                int translatedTarget = vertexIdTranslate.forward(target);
                sourceUnallowed.add(translatedTarget);
                Set<Integer> targetUnallowed = unallowedWalks.get(translatedTarget);
                if (targetUnallowed == null) {
                    targetUnallowed = new HashSet<Integer>();
                    unallowedWalks.put(translatedTarget, targetUnallowed);
                }
                targetUnallowed.add(translatedSource);
            }
        }

        try {
            companion = new PathFollowerCompanion(4, Runtime.getRuntime().maxMemory() / 3,
                    vertexIdTranslate, pathTypes, companionAcceptPolicy, allowedTargets);
        } catch(RemoteException e) {
            throw new RuntimeException(e);
        }

        Collections.sort(sources);
        DrunkardJob drunkardJob = this.drunkardMobEngine.addJob("pathFollower",
                EdgeDirection.IN_AND_OUT_EDGES, this, companion);
        drunkardJob.configureWalkSources(sources, numWalksPerPath * numPaths);
        sourceIds = drunkardJob.getWalkManager().getSources();
        companion.setSources(sourceIds);
    }

    public void execute() {
        try {
            drunkardMobEngine.run(numIters);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<MatrixRow> getFeatureMatrix() {
        return companion.getFeatureMatrix(sourcesMap);
    }

    public void shutDown() {
        logger.info("Path Follower shutting down");
        companion.close();
    }

    /**
     * WalkUpdateFunction interface implementations
     */
    @Override
    public void processWalksAtVertex(WalkArray walkArray,
                                     ChiVertex<EmptyType, Integer> vertex,
                                     DrunkardContext drunkardContext_,
                                     Random random) {
        long[] walks = ((LongWalkArray)walkArray).getArray();
        LongDrunkardContext drunkardContext = (LongDrunkardContext) drunkardContext_;
        int numWalks = walks.length;
        int vertexId = vertex.getId();
        int inEdges = vertex.numInEdges();
        int outEdges = vertex.numOutEdges();
        int numEdges = inEdges + outEdges;

        if (numEdges == 0) {
            // Nothing to do; this means that a walk started at a node that had no connecting
            // edges, so we don't even bother restarting the walks.
            return;
        }

        // Build a map of the edges, so we don't have to do a loop for every single walk (of which
        // there are very many).
        Map<Integer, List<Integer>> inEdgeMap = new HashMap<Integer, List<Integer>>();
        for (int edgeNum=0; edgeNum<inEdges; edgeNum++) {
            ChiEdge<Integer> edge = vertex.inEdge(edgeNum);
            List<Integer> edgeSet = inEdgeMap.get(edge.getValue());
            if (edgeSet == null) {
                edgeSet = new ArrayList<Integer>();
                inEdgeMap.put(edge.getValue(), edgeSet);
            }
            edgeSet.add(edge.getVertexId());
        }
        Map<Integer, List<Integer>> outEdgeMap = new HashMap<Integer, List<Integer>>();
        for (int edgeNum=0; edgeNum<outEdges; edgeNum++) {
            ChiEdge<Integer> edge = vertex.outEdge(edgeNum);
            List<Integer> edgeSet = outEdgeMap.get(edge.getValue());
            if (edgeSet == null) {
                edgeSet = new ArrayList<Integer>();
                outEdgeMap.put(edge.getValue(), edgeSet);
            }
            edgeSet.add(edge.getVertexId());
        }

        // Now we go through the walks.  Basic outline: for each walk, get its path type and hop
        // number, randomly pick an edge that matches the path for the hop number, and advance the
        // walk.
        for (long walk : walks) {
            int pathType = Manager.pathType(walk);
            int numHops = pathTypes[pathType].numHops;
            int hopNum = Manager.hopNum(walk);

            /* For debugging
            int sourceV = vertexIdTranslate.backward(sourceIds[staticSourceIdx(walk)]);
            int at = vertexIdTranslate.backward(vertex.getId());
            System.out.println("walk from " + sourceV + " at " + at + " following path " + pathType
                    + " hop number " + hopNum);
            */

            if (hopNum >= numHops) {
                resetWalk(walk, drunkardContext);
                continue;
            }
            boolean trackBit = hopNum == numHops - 1;
            boolean reverse = getReverse(pathType, hopNum);
            int edgeType = getEdgeType(pathType, hopNum);
            List<Integer> possibleHops;
            if (reverse) {
                possibleHops = inEdgeMap.get(edgeType);
            } else {
                possibleHops = outEdgeMap.get(edgeType);
            }
            if (possibleHops == null) {
                resetWalk(walk, drunkardContext);
                continue;
            }
            int nextVertex  = possibleHops.get(random.nextInt(possibleHops.size()));
            // Prohibit walks that follow training data edges.  Maybe there's a faster way
            // to do this, with arrays, but it would take a lot of bookkeeping, and the
            // speed improvements may not be necessary.
            if (walkNotAllowed(edgeType, vertexId, nextVertex)) {
                resetWalk(walk, drunkardContext);
                continue;
            }
            walk = Manager.incrementHopNum(walk);
            drunkardContext.forwardWalkTo(walk, nextVertex, trackBit);
        }
    }

    private int getEdgeType(int pathType, int hopNum) {
        return pathTypes[pathType].edgeTypes[hopNum];
    }

    private boolean getReverse(int pathType, int hopNum) {
        return pathTypes[pathType].reverse[hopNum];
    }

    private boolean walkNotAllowed(int edgeType, int vertexId, int nextVertex) {
        if (unallowedEdges != null && unallowedEdges.contains(edgeType)) {
            Set<Integer> unallowed = unallowedWalks.get(vertexId);
            if (unallowed != null && unallowed.contains(nextVertex)) {
                return true;
            }
        }
        return false;
    }

    private void resetWalk(long walk, LongDrunkardContext drunkardContext) {
        walk = Manager.encode(Manager.pathType(walk), 0, staticSourceIdx(walk),
                staticTrackBit(walk), staticOff(walk));
        drunkardContext.resetWalk(walk, false);
    }

    @Override
    /**
     * We don't want to ignore any vertices for this walk.
     */
    public int[] getNotTrackedVertices(ChiVertex<EmptyType, Integer> vertex) {
        int[] notCounted = new int[0];
        return notCounted;
    }

    /////////////////////////////////////////////////////////////////////////
    // Some boilerplate to modify the behavior of DrunkardMobEngine slightly
    /////////////////////////////////////////////////////////////////////////

    class Factory extends LongDrunkardFactory<EmptyType, Integer> {
        public WalkManager createWalkManager(int numVertices, int numSources) {
            return new Manager(numVertices, numSources, numPaths);
        }
    }


    static class Manager extends LongWalkManager {
        private int nextPathType;
        private int numPaths;
        public final int MAX_PATH_TYPES = (int) Math.pow(2, 27);
        public final int MAX_HOPS = (int) Math.pow(2, 5);

        Manager(int numVertices, int numSources, int numPaths) {
            super(numVertices, numSources);
            nextPathType = -1;
            this.numPaths = numPaths;
        }

        /**
         * We take the standard (sourceId, track, off) encoding from IntWalkManager and put some
         * walk stuff in front of it - 27 bits for the path type, and 5 bits for the hop number
         * (both of those are longer than we really need, but that's ok).
         *
         * Because we're using the same lower 32 bits, also, we don't have to override the
         * sourceIdx, trackBit, and off methods of the base LongWalkManager, which doesn't use the
         * top 32 bits.
         */
        static long encode(int pathType, int hopNum, int sourceId, boolean track, int off) {
            assert(off < 128);
            int trackBit = (track ? 1 : 0);
            return ((long) pathType & 0x7ffffff) << 37 |
                ((long)(hopNum & 0x1f)) << 32 |
                (long)((sourceId & 0xffffffL) << 8) |
                (long)((off & 0x7f) << 1) | (long)trackBit;
        }

        public static int pathType(long walk) {
            return (int) (walk >> 37) & 0x7ffffff;
        }

        public static int hopNum(long walk) {
            return (int) (walk >> 32) & 0x1f;
        }

        public static long incrementHopNum(long walk) {
            int hopNum = hopNum(walk);
            return encode(pathType(walk), hopNum+1, staticSourceIdx(walk), staticTrackBit(walk),
                    staticOff(walk));
        }

        @Override
        protected long encode(int sourceId, boolean trackBit, int off) {
            throw new RuntimeException("This implementation needs a walk id!");
        }

        @Override
        protected long encodeV(int sourceId, boolean trackBit, int vertexId) {
            throw new RuntimeException("Use encodeNewWalk instead of this method, so we can do " +
                    "the walkId correctly");
        }

        @Override
        protected long encodeNewWalk(int sourceId, int sourceVertex, boolean trackBit) {
            // This is assuming that encodeNewWalk is called repeatedly on each source vertex until
            // the correct number of walks is reached.  As of right now, that's how it works, but
            // if things change, this implementation will likely also need to change.
            nextPathType = (nextPathType + 1) % numPaths;
            return encode(nextPathType, 0, sourceId, trackBit, sourceVertex % bucketSize);
        }

        @Override
        protected long reencodeWalk(long walk, int toVertex, boolean trackBit) {
            int pathType = pathType(walk);
            int hopNum = hopNum(walk);
            return encode(pathType, hopNum, sourceIdx(walk), trackBit, toVertex % bucketSize);
        }
    }

    // These next three are for convenience, so that Path.encode doesn't need an instance of
    // Manager.  Kind of ugly, but oh well...
    public static int staticSourceIdx(long walk) {
        return (int) ((walk & 0xffffff00) >> 8) & 0xffffff;
    }

    public static boolean staticTrackBit(long walk) {
        return ((walk & 1) != 0);
    }

    public static int staticOff(long walk) {
        return (int) (walk >> 1) & 0x7f;
    }
}
