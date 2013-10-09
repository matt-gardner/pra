package edu.cmu.ml.rtw.pra;

import edu.cmu.graphchi.*;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.walks.BucketsToSend;
import edu.cmu.graphchi.walks.DrunkardContext;
import edu.cmu.graphchi.walks.DrunkardDriver;
import edu.cmu.graphchi.walks.DrunkardJob;
import edu.cmu.graphchi.walks.DrunkardMobEngine;
import edu.cmu.graphchi.walks.LongDrunkardContext;
import edu.cmu.graphchi.walks.LongDrunkardDriver;
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

import edu.cmu.ml.rtw.util.Dictionary;
import edu.cmu.ml.rtw.util.Pair;

/**
 * Finds common path types between pairs of nodes in a given set, using DrunkardMobEngine.
 * @author Matt Gardner
 */
public class PathFinder implements WalkUpdateFunction<EmptyType, Integer> {

    private static double RESET_PROBABILITY = 0.35;
    private static Logger logger = ChiLogger.getLogger("path-finder");
    private DrunkardMobEngine<EmptyType, Integer>  drunkardMobEngine;
    private PathFinderCompanion companion;
    private PathEncoder pathEncoder;
    private int numWalksPerSource;
    private Path[] walkPaths;
    private int MAX_HOPS = 10;
    private int[][][] encodedWalkPaths;
    private Dictionary pathDict;
    private int[] sourceIds;
    private List<Integer> origSources;
    private List<Integer> origTargets;
    // These maps might be a little slow; I'll have to look at performance and see if it's worth it
    // to change this.
    private Map<Integer, Set<Integer>> unallowedWalks;
    private Set<Integer> unallowedEdges;
    private VertexIdTranslate vertexIdTranslate;
    private Object printLock = new Object();

    public PathFinder(String baseFilename,
                      int nShards,
                      List<Integer> origSources,
                      List<Integer> origTargets,
                      Set<Integer> unallowedEdges,
                      int walksPerSource,
                      PathTypePolicy policy,
                      PathEncoder pathEncoder) {
        try {
            this.drunkardMobEngine = new DrunkardMobEngine<EmptyType, Integer>(baseFilename,
                    nShards, new Factory());
            this.drunkardMobEngine.setEdataConverter(new IntConverter());
        } catch (IOException e) {
            throw new RuntimeException("IOException when creating DrunkardMobEngine");
        }
        vertexIdTranslate = this.drunkardMobEngine.getVertexIdTranslate();
        this.pathEncoder = pathEncoder;
        this.numWalksPerSource = walksPerSource;
        this.unallowedEdges = unallowedEdges;
        this.origSources = origSources;
        this.origTargets = origTargets;
        this.pathDict = new Dictionary();
        unallowedWalks = new HashMap<Integer, Set<Integer>>();
        // We add these to a set first, so we don't start twice as many walks from a node that
        // shows up twice in the training data.  You could argue that those nodes should have more
        // influence on the resultant paths, and that's fair, but it also slows down the
        // computation by quite a bit, because the walks aren't evenly distributed, so there is
        // often one thread that takes a long time to finish each iteration.
        Set<Integer> allSourceNodes = new HashSet<Integer>();
        for (int i=0; i<origSources.size(); i++) {
            int translatedSource = vertexIdTranslate.forward(origSources.get(i));
            allSourceNodes.add(translatedSource);
            Set<Integer> unallowed = unallowedWalks.get(translatedSource);
            if (unallowed == null) {
                unallowed = new HashSet<Integer>();
                unallowedWalks.put(translatedSource, unallowed);
            }

            if (origTargets != null) {
                int translatedTarget = vertexIdTranslate.forward(origTargets.get(i));
                allSourceNodes.add(translatedTarget);
                unallowed.add(translatedTarget);
                unallowed = unallowedWalks.get(translatedTarget);
                if (unallowed == null) {
                    unallowed = new HashSet<Integer>();
                    unallowedWalks.put(translatedTarget, unallowed);
                }
                unallowed.add(translatedSource);
            }
        }
        List<Integer> sources = new ArrayList<Integer>(allSourceNodes);
        Collections.sort(sources);
        try {
            companion = new PathFinderCompanion(4,  // numThreads
                                                Runtime.getRuntime().maxMemory() / 3,
                                                vertexIdTranslate,
                                                pathDict,
                                                policy);
        } catch(RemoteException e) {
            throw new RuntimeException(e);
        }
        DrunkardJob drunkardJob = this.drunkardMobEngine.addJob("pathFinder",
                EdgeDirection.IN_AND_OUT_EDGES, this, companion);

        /* Configure walk sources. Note, GraphChi's internal ids are used. */
        drunkardJob.configureWalkSources(sources, numWalksPerSource);
        sourceIds = drunkardJob.getWalkManager().getSources();
        companion.setSources(sourceIds);
        int numWalks = sources.size() * numWalksPerSource;
        walkPaths = new Path[numWalks];
        encodedWalkPaths = new int[numWalks][MAX_HOPS][];
    }

    public void execute(int numIters) {
        try {
            drunkardMobEngine.run(numIters);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Integer> getPathCounts() {
        return companion.getPathCounts(origSources, origTargets);
    }

    /**
     * Returns a set of rows from the feature matrix computed by the walks done.  Also returns a
     * list of path types, which specify the columns of the feature matrix, so we know how to
     * interpret the feature numbers in the matrix rows.
     */
    public Pair<Dictionary, List<MatrixRow>> getFeatureMatrix() {
        return new Pair<Dictionary, List<MatrixRow>>(pathDict, companion.getFeatureMatrix());
    }

    public void shutDown() {
        logger.info("Path Finder shutting down");
        companion.close();
    }

    public int[] encodePath(Path path) {
        String[] encodedPathStrings = pathEncoder.encode(path);
        int[] encodedPaths = new int[encodedPathStrings.length];
        for (int i = 0; i < encodedPaths.length; i++) {
            encodedPaths[i] = pathDict.getIndex(encodedPathStrings[i]);
        }
        return encodedPaths;
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
        int inEdges = vertex.numInEdges();
        int outEdges = vertex.numOutEdges();
        int numEdges = inEdges + outEdges;
        int vertexId = vertex.getId();

        // If there are no edges connected to this node, we just give up.  No need to restart any
        // walks, as they'll just come back here and waste our time again.  If this happens, if
        // means you gave a source (or target) node that was completely disconnected from the rest
        // of the graph.
        if (numEdges == 0) {
            return;
        }

        // Advance each walk to a random edge.
        for (long walk : walks) {
            int walkId = Manager.walkId(walk);

            // Reset?
            if (random.nextDouble() < RESET_PROBABILITY) {
                resetWalk(walk, walkId, drunkardContext);
                continue;
            }

            Path path = walkPaths[walkId];
            if (path != null && path.getHops() >= MAX_HOPS - 1) {
                resetWalk(walk, walkId, drunkardContext);
                continue;
            }
            if (path == null) {
                path = new Path(sourceIds[staticSourceIdx(walk)], MAX_HOPS);
                walkPaths[walkId] = path;
            }
            boolean reverse;
            ChiEdge<Integer> edge;
            int edgeNum = random.nextInt(numEdges);
            if (edgeNum < inEdges) {
                reverse = true;
                edge = vertex.inEdge(edgeNum);
            } else {
                reverse = false;
                edge = vertex.outEdge(edgeNum - inEdges);
            }
            int nextVertex  = edge.getVertexId();
            // Prohibit cyclical walks
            if (path.alreadyVisited(nextVertex)) {
                resetWalk(walk, walkId, drunkardContext);
                continue;
            }
            int edgeType = edge.getValue();
            // Prohibit walks that follow training data edges.  Maybe there's a faster way
            // to do this, with arrays, but it would take a lot of bookkeeping, and the
            // speed improvements may not be necessary.
            if (unallowedEdges.contains(edgeType)) {
                Set<Integer> unallowed = unallowedWalks.get(vertexId);
                if (unallowed != null && unallowed.contains(nextVertex)) {
                    resetWalk(walk, walkId, drunkardContext);
                    continue;
                }
            }
            path.addHop(nextVertex, edgeType, reverse);
            walk = Manager.incrementHopNum(walk);
            int hopNum = Manager.hopNum(walk);
            encodedWalkPaths[walkId][hopNum] = encodePath(path);
            drunkardContext.forwardWalkTo(walk, nextVertex, true);
        }
    }

    private void resetWalk(long walk, int walkId, LongDrunkardContext drunkardContext) {
        walkPaths[walkId] = null;
        walk = Manager.resetHopNum(walk);
        drunkardContext.resetWalk(walk, true);
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

    public long[] encodeWalkForCompanion(long walk) {
        int walkId = Manager.walkId(walk);
        int hopNum = Manager.hopNum(walk);
        int[] walkPathTypes = encodedWalkPaths[walkId][hopNum];
        if (walkPathTypes == null) {
            return new long[0];
        }
        int numPathTypes = walkPathTypes.length;
        long[] encodedWalks = new long[numPathTypes];
        for (int i = 0; i < numPathTypes; i++) {
            // Replace the walk id with the path type - the companion needs to be able to get
            // (source, target, path) triples.  Our regular encoding already has source and target,
            // so we just need to put path type in.
            int pathType = walkPathTypes[i];
            if (pathType == 0) {
                // This is because the track bit doesn't work like I expected, and I'm not sure at
                // what point the walks actually get sent to the companion.  This way, we just
                // store everything that can be sent to the companion, and if it's not here, tell
                // the companion that by sending 0.
                encodedWalks[i] = 0;
            } else {
                encodedWalks[i] = Manager.encodeForCompanion(pathType,
                                                             staticSourceIdx(walk),
                                                             staticTrackBit(walk),
                                                             staticOff(walk));
            }
        }
        return encodedWalks;
    }

    class Factory extends LongDrunkardFactory<EmptyType, Integer> {
        public WalkManager createWalkManager(int numVertices, int numSources) {
            return new Manager(numVertices, numSources);
        }
        public DrunkardDriver<EmptyType, Integer> createDrunkardDriver(DrunkardJob job,
                WalkUpdateFunction<EmptyType, Integer> callback) {
            return new Driver(job, callback);
        }
    }

    class Driver extends LongDrunkardDriver<EmptyType, Integer> {
        Driver(final DrunkardJob job, WalkUpdateFunction<EmptyType, Integer> callback) {
            super(job, callback);
        }

        @Override
        protected LongDumperThread createDumperThread() {
            return new DumperThread();
        }
        class DumperThread extends LongDrunkardDriver<EmptyType, Integer>.LongDumperThread {
            @Override
            protected void processWalks(BucketsToSend bucket, int i) {
                LongWalkArray bucketWalks = (LongWalkArray) bucket.walks;
                long w = bucketWalks.getArray()[i];
                LongWalkManager manager = (LongWalkManager) job.getWalkManager();
                int v = manager.off(w) + bucket.firstVertex;


                // Skip walks with the track-bit not set
                boolean trackBit = manager.trackBit(w);

                if (!trackBit) {
                    return;
                }

                // The only difference with LongDrunkardDriver.LongDumperThread
                for (long encodedWalk : encodeWalkForCompanion(w)) {
                    walks[idx] = encodedWalk;
                    vertices[idx] = v;
                    idx++;

                    if (idx >= walks.length) {
                        try {
                            job.getCompanion().processWalks(new LongWalkArray(walks), vertices);
                        } catch (Exception err) {
                            err.printStackTrace();
                        }
                        idx = 0;
                    }
                }
            }
        }
    }


    static class Manager extends LongWalkManager {
        private int nextWalkId;
        public final int MAX_ENCODABLE_WALKS = (int) Math.pow(2, 27);
        public final int MAX_ENCODABLE_HOPS = (int) Math.pow(2, 5);

        Manager(int numVertices, int numSources) {
            super(numVertices, numSources);
        }

        /**
         * We take the standard (sourceId, hop, off) encoding from IntWalkManager and put some walk
         * stuff in front of it - 27 bits for the walk id, and 5 bits for the hop number.
         *
         * Because we're using the same lower 32 bits, also, we don't have to override the
         * sourceIdx, trackBit, and off methods of the base LongWalkManager, which doesn't use the
         * top 32 bits.
         */
        static long encode(int walkId, int hopNum, int sourceId, boolean track, int off) {
            assert(off < 128);
            int trackBit = (track ? 1 : 0);
            return ((long) walkId & 0x7ffffff) << 37 |
                ((long)(hopNum & 0x1f)) << 32 |
                (long)((sourceId & 0xffffffL) << 8) |
                (long)((off & 0x7f) << 1) | (long)trackBit;
        }

        static long encodeForCompanion(int pathType, int sourceId, boolean track, int off) {
            assert(off < 128);
            int trackBit = (track ? 1 : 0);
            return ((long) pathType) << 32 | (long)((sourceId & 0xffffffL) << 8) |
                    (long)((off & 0x7f) << 1) | (long)trackBit;
        }

        public static int walkId(long walk) {
            return (int) (walk >> 37) & 0x7ffffff;
        }

        public static int hopNum(long walk) {
            return (int) (walk >> 32) & 0x1f;
        }

        public static long incrementHopNum(long walk) {
            int hopNum = hopNum(walk);
            return encode(walkId(walk), hopNum+1, staticSourceIdx(walk), staticTrackBit(walk),
                    staticOff(walk));
        }

        public static long resetHopNum(long walk) {
            return encode(walkId(walk), 0, staticSourceIdx(walk), staticTrackBit(walk),
                    staticOff(walk));
        }

        public static int pathType(long walkEncodedForCompanion) {
            return (int) (walkEncodedForCompanion >> 32);
        }

        @Override
        protected long encode(int sourceId, boolean hop, int off) {
            throw new RuntimeException("This implementation needs a walk id!");
        }

        @Override
        protected long encodeV(int sourceId, boolean hop, int vertexId) {
            throw new RuntimeException("Use encodeNewWalk instead of this method, so we can do " +
                    "the walkId correctly");
        }

        @Override
        protected long encodeNewWalk(int sourceId, int sourceVertex, boolean hop) {
            return encode(nextWalkId++, 0, sourceId, hop, sourceVertex % bucketSize);
        }

        @Override
        protected long reencodeWalk(long walk, int toVertex, boolean trackBit) {
            int walkId = walkId(walk);
            int hopNum = hopNum(walk);
            return encode(walkId, hopNum, sourceIdx(walk), trackBit, toVertex % bucketSize);
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

    public static long setTrackBit(long walk, boolean trackBit) {
        if (walk % 2 == 0) {
            // Track bit not set
            if (trackBit) {
                return walk + 1;
            } else {
                return walk;
            }
        } else {
            // Track bit is currently set
            if (trackBit) {
                return walk;
            } else {
                return walk - 1;
            }
        }
    }
}
