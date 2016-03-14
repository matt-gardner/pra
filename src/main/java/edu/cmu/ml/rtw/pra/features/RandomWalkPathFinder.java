package edu.cmu.ml.rtw.pra.features;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import edu.cmu.graphchi.ChiLogger;
import edu.cmu.graphchi.ChiVertex;
import edu.cmu.graphchi.EdgeDirection;
import edu.cmu.graphchi.EmptyType;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.walks.BucketsToSend;
import edu.cmu.graphchi.walks.DrunkardContext;
import edu.cmu.graphchi.walks.DrunkardDriver;
import edu.cmu.graphchi.walks.DrunkardJob;
import edu.cmu.graphchi.walks.DrunkardMobEngine;
import edu.cmu.graphchi.walks.LongDrunkardContext;
import edu.cmu.graphchi.walks.LongDrunkardDriver;
import edu.cmu.graphchi.walks.LongDumperThread;
import edu.cmu.graphchi.walks.LongDrunkardFactory;
import edu.cmu.graphchi.walks.LongWalkArray;
import edu.cmu.graphchi.walks.LongWalkManager;
import edu.cmu.graphchi.walks.WalkArray;
import edu.cmu.graphchi.walks.WalkManager;
import edu.cmu.graphchi.walks.WalkUpdateFunction;
import edu.cmu.ml.rtw.pra.data.NodePairInstance;
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk;
import com.mattg.util.FileUtil;
import com.mattg.util.MutableConcurrentIndex;
import com.mattg.util.Pair;

/**
 * Finds common path types between pairs of nodes in a given set, using DrunkardMobEngine.
 * @author Matt Gardner
 */
public class RandomWalkPathFinder implements WalkUpdateFunction<EmptyType, Integer> {

  public static final int MAX_HOPS = 10;
  private static final Logger logger = ChiLogger.getLogger("path-finder");
  private final DrunkardMobEngine<EmptyType, Integer>  drunkardMobEngine;
  private final RandomWalkPathFinderCompanion companion;
  private final PathTypeFactory pathTypeFactory;
  private final int numWalksPerSource;
  private final Path[] walkPaths;
  private final int[][][] encodedWalkPaths;
  private final MutableConcurrentIndex<PathType> pathDict;
  private final int[] sourceIds;
  private final List<Integer> origSources;
  private final List<Integer> origTargets;
  private final List<NodePairInstance> instances;
  private final EdgeExcluder edgeExcluder;
  private final VertexIdTranslate vertexIdTranslate;
  private final Object printLock = new Object();

  private double resetProbability = 0.35;

  public RandomWalkPathFinder(GraphOnDisk graph,
                              List<NodePairInstance> instances,
                              EdgeExcluder edgeExcluder,
                              int walksPerSource,
                              PathTypePolicy policy,
                              PathTypeFactory pathTypeFactory) {
    try {
      this.drunkardMobEngine =
          new DrunkardMobEngine<EmptyType, Integer>(graph.graphFile(), graph.numShards(), new Factory());
      this.drunkardMobEngine.setEdataConverter(new IntConverter());
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("IOException when creating DrunkardMobEngine", e);
    }
    vertexIdTranslate = this.drunkardMobEngine.getVertexIdTranslate();
    this.edgeExcluder = edgeExcluder;
    edgeExcluder.prepUnallowedWalks(vertexIdTranslate);
    this.pathTypeFactory = pathTypeFactory;
    this.numWalksPerSource = walksPerSource;
    this.instances = instances;
    origSources = Lists.newArrayList();
    origTargets = Lists.newArrayList();
    for (NodePairInstance instance : instances) {
      origSources.add(instance.source());
      origTargets.add(instance.target());
    }
    this.pathDict = new MutableConcurrentIndex<PathType>(pathTypeFactory, false, new FileUtil());
    // We add these to a set first, so we don't start twice as many walks from a node that shows up
    // twice in the training data.  You could argue that those nodes should have more influence on
    // the resultant paths, and that's fair, but it also slows down the computation by quite a bit,
    // because the walks aren't evenly distributed, so there is often one thread that takes a long
    // time to finish each iteration.
    Set<Integer> allSourceNodes = new HashSet<Integer>();
    for (int i=0; i<origSources.size(); i++) {
      if (origSources.get(i) > drunkardMobEngine.getEngine().numVertices()) {
        logger.warning("Found a node with a higher index than is in the graph: " + origSources.get(i));
        continue;
      }
      int translatedSource = vertexIdTranslate.forward(origSources.get(i));
      allSourceNodes.add(translatedSource);
      if (origTargets != null) {
        if (origTargets.get(i) > drunkardMobEngine.getEngine().numVertices()) {
          logger.warning("Found a node with a higher index than is in the graph: " + origTargets.get(i));
          continue;
        }
        int translatedTarget = vertexIdTranslate.forward(origTargets.get(i));
        allSourceNodes.add(translatedTarget);
      }
    }

    List<Integer> sources = new ArrayList<Integer>(allSourceNodes);
    Collections.sort(sources);
    try {
      companion = new RandomWalkPathFinderCompanion(graph,
                                                    4,  // numThreads
                                                    Runtime.getRuntime().maxMemory() / 3,
                                                    vertexIdTranslate,
                                                    pathDict,
                                                    pathTypeFactory,
                                                    policy);
    } catch(RemoteException e) {
      throw new RuntimeException(e);
    }
    DrunkardJob drunkardJob =
        this.drunkardMobEngine.addJob("pathFinder", EdgeDirection.IN_AND_OUT_EDGES, this, companion);

    /* Configure walk sources. Note, GraphChi's internal ids are used. */
    drunkardJob.configureWalkSources(sources, numWalksPerSource);
    sourceIds = drunkardJob.getWalkManager().getSources();
    companion.setSources(sourceIds);
    int numWalks = sources.size() * numWalksPerSource;
    walkPaths = new Path[numWalks];
    encodedWalkPaths = new int[numWalks][MAX_HOPS][];
  }

  public void setResetProbability(double resetProbability) {
    this.resetProbability = resetProbability;
  }

  public void execute(int numIters) {
    try {
      drunkardMobEngine.run(numIters);
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<PathType, Integer> getPathCounts() {
    return companion.getPathCounts(origSources, origTargets);
  }

  public Map<NodePairInstance, Map<PathType, Integer>> getPathCountMap() {
    return companion.getPathCountMap(instances);
  }

  public Map<NodePairInstance, Map<PathType, Set<Pair<Integer, Integer>>>> getLocalSubgraphs() {
    return companion.getLocalSubgraphs(instances);
  }

  public void shutDown() {
    logger.info("Path Finder shutting down");
    companion.close();
    pathDict.clear();
  }

  public int[] encodePath(Path path) {
    PathType[] encodedPathTypes = pathTypeFactory.encode(path);
    int[] encodedPaths = new int[encodedPathTypes.length];
    for (int i = 0; i < encodedPaths.length; i++) {
      encodedPaths[i] = pathDict.getIndex(encodedPathTypes[i]);
    }
    return encodedPaths;
  }

  @VisibleForTesting
  protected MutableConcurrentIndex<PathType> getPathDictionary() {
    return pathDict;
  }

  @VisibleForTesting
  protected void setEncodedWalkPaths(int[] encoded, int walkId, int hopNum) {
    encodedWalkPaths[walkId][hopNum] = encoded;
  }

  @VisibleForTesting
  protected void setWalkPath(Path path, int walkId) {
    walkPaths[walkId] = path;
  }

  @VisibleForTesting
  protected Path getWalkPath(int walkId) {
    return walkPaths[walkId];
  }

  /**
   * WalkUpdateFunction interface implementations
   */
  @Override
  public void processWalksAtVertex(WalkArray walkArray,
                                   ChiVertex<EmptyType, Integer> chiVertex,
                                   DrunkardContext drunkardContext_,
                                   Random random) {
    long[] walks = ((LongWalkArray)walkArray).getArray();
    LongDrunkardContext drunkardContext = (LongDrunkardContext) drunkardContext_;
    Vertex vertex = new Vertex(chiVertex, false);

    // If there are no edges connected to this node, we just give up.  No need to restart any
    // walks, as they'll just come back here and waste our time again.  If this happens, if means
    // you gave a source (or target) node that was completely disconnected from the rest of the
    // graph.
    if (vertex.getNumEdges() == 0) {
      return;
    }

    // Advance each walk to a random edge.
    for (long walk : walks) {
      processSingleWalkAtVertex(walk, vertex, drunkardContext, random);
    }
  }

  /**
   * Take a single walk, and advance it.
   *
   * We take a bunch of parameters here that we probably don't need to, but it's in the hope of
   * reducing the cost of computation in this method, because it gets called a _lot_.
   */
  @VisibleForTesting
  protected void processSingleWalkAtVertex(long walk,
                                           Vertex vertex,
                                           LongDrunkardContext drunkardContext,
                                           Random random) {
    int walkId = Manager.walkId(walk);
    int sourceVertex = sourceIds[staticSourceIdx(walk)];

    // Reset?
    if (random.nextDouble() < resetProbability) {
      resetWalk(walk, walkId, drunkardContext);
      return;
    }

    Path path = walkPaths[walkId];
    if (path != null && path.getHops() >= MAX_HOPS - 1) {
      resetWalk(walk, walkId, drunkardContext);
      return;
    }
    if (path == null) {
      path = new Path(sourceIds[staticSourceIdx(walk)], MAX_HOPS);
      walkPaths[walkId] = path;
    }
    boolean reverse;
    int edgeNum = random.nextInt(vertex.getNumEdges());
    if (edgeNum < vertex.getNumInEdges()) {
      reverse = true;
    } else {
      reverse = false;
    }
    int nextVertex  = vertex.getEdgeNode(edgeNum);
    // Prohibit cyclical walks
    if (path.alreadyVisited(nextVertex)) {
      resetWalk(walk, walkId, drunkardContext);
      return;
    }
    int edgeType = vertex.getEdgeType(edgeNum);
    if (edgeExcluder.shouldExcludeEdge(sourceVertex, nextVertex, vertex, edgeType)) {
      resetWalk(walk, walkId, drunkardContext);
      return;
    }
    path.addHop(nextVertex, edgeType, reverse);
    walk = Manager.incrementHopNum(walk);
    int hopNum = Manager.hopNum(walk);
    encodedWalkPaths[walkId][hopNum] = encodePath(path);
    drunkardContext.forwardWalkTo(walk, nextVertex, true);
  }

  private void resetWalk(long walk, int walkId, LongDrunkardContext drunkardContext) {
    walkPaths[walkId] = null;
    walk = Manager.resetHopNum(walk);
    drunkardContext.resetWalk(walk, true);
  }

  /**
   * We don't want to ignore any vertices for this walk.
   */
  @Override
  public int[] getNotTrackedVertices(ChiVertex<EmptyType, Integer> vertex) {
    int[] notCounted = new int[0];
    return notCounted;
  }

  /////////////////////////////////////////////////////////////////////////
  // Some boilerplate to modify the behavior of DrunkardMobEngine slightly
  /////////////////////////////////////////////////////////////////////////

  @VisibleForTesting
  protected long[] encodeWalkForCompanion(long walk) {
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
      // (source, target, path) triples.  Our regular encoding already has source and target, so we
      // just need to put path type in.
      int pathType = walkPathTypes[i];
      encodedWalks[i] = Manager.encodeForCompanion(pathType,  // path
                                                   staticSourceIdx(walk),  // source
                                                   staticTrackBit(walk),
                                                   staticOff(walk));  // target
    }
    return encodedWalks;
  }

  class Factory extends LongDrunkardFactory<EmptyType, Integer> {
    public WalkManager createWalkManager(int numVertices, int numSources) {
      return new Manager(numVertices, numSources);
    }
    public DrunkardDriver<EmptyType, Integer> createDrunkardDriver(DrunkardJob job,
                                                                   WalkUpdateFunction<EmptyType,
                                                                   Integer> callback) {
      return new Driver(job, callback);
    }
  }

  class Driver extends LongDrunkardDriver<EmptyType, Integer> {
    Driver(final DrunkardJob job, WalkUpdateFunction<EmptyType, Integer> callback) {
      super(job, callback);
    }

    @Override
    protected LongDumperThread createDumperThread() {
      return new DumperThread(bucketQueue, pendingWalksToSubmit, finished, job);
    }

    class DumperThread extends LongDumperThread {

      DumperThread(LinkedBlockingQueue<BucketsToSend> bucketQueue,
                          AtomicLong pendingWalksToSubmit,
                          AtomicBoolean finished,
                          DrunkardJob job) {
        super(bucketQueue, pendingWalksToSubmit, finished, job);
      }

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
    public static final int MAX_ENCODABLE_WALKS = (int) Math.pow(2, 27);
    public static final int MAX_ENCODABLE_HOPS = (int) Math.pow(2, 5);
    public static final int MAX_SOURCES = (int) Math.pow(2, 24);

    Manager(int numVertices, int numSources) {
      super(numVertices, numSources);
    }

    /**
     * We take the standard (sourceId, hop, off) encoding from IntWalkManager and put some walk
     * stuff in front of it - 27 bits for the walk id, and 5 bits for the hop number.
     *
     * Because we're using the same lower 32 bits, also, we don't have to override the sourceIdx,
     * trackBit, and off methods of the base LongWalkManager, which doesn't use the top 32 bits.
     */
    static long encode(int walkId, int hopNum, int sourceId, boolean track, int off) {
      if (off >= 128) throw new AssertionError("off too high: " + off);
      if (walkId >= MAX_ENCODABLE_WALKS) throw new AssertionError("walkId too high: " + walkId);
      if (hopNum >= MAX_ENCODABLE_HOPS) throw new AssertionError("hopNum too high: " + hopNum);
      if (sourceId >= MAX_SOURCES) throw new AssertionError("sourceId too high: " + sourceId);
      int trackBit = (track ? 1 : 0);
      return ((long) walkId & 0x7ffffff) << 37 |
          ((long)(hopNum & 0x1f)) << 32 |
          (long)((sourceId & 0xffffffL) << 8) |
          (long)((off & 0x7f) << 1) | (long)trackBit;
    }

    static long encodeForCompanion(int pathType, int sourceId, boolean track, int off) {
      assert(off < 128);
      int trackBit = (track ? 1 : 0);
      return ((long) pathType) << 32 |
          (long)((sourceId & 0xffffffL) << 8) |
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
      return encode(walkId(walk),
                    hopNum+1,
                    staticSourceIdx(walk),
                    staticTrackBit(walk),
                    staticOff(walk));
    }

    public static long resetHopNum(long walk) {
      return encode(walkId(walk), 0, staticSourceIdx(walk), staticTrackBit(walk), staticOff(walk));
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
