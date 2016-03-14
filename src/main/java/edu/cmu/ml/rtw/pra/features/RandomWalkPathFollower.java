package edu.cmu.ml.rtw.pra.features;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.ChiEdge;
import edu.cmu.graphchi.ChiLogger;
import edu.cmu.graphchi.ChiVertex;
import edu.cmu.graphchi.EdgeDirection;
import edu.cmu.graphchi.EmptyType;
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
import edu.cmu.ml.rtw.pra.data.NodePairInstance;
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk;
import com.mattg.util.MapUtil;
import com.mattg.util.Pair;

/**
 * Finds common path types between pairs of nodes in a given set, using DrunkardMobEngine.
 * @author Matt Gardner
 */
public class RandomWalkPathFollower implements PathFollower, WalkUpdateFunction<EmptyType, Integer> {

  private static Logger logger = ChiLogger.getLogger("path-follower");
  private final DrunkardMobEngine<EmptyType, Integer>  drunkardMobEngine;
  private final int numWalksPerPath;
  private final int[] sourceIds;
  private final int numPaths;
  private final int numIters;
  private final PathType[] pathTypes;
  private final Map<Integer, Set<Integer>> sourcesMap;
  private final List<NodePairInstance> instances;
  private final EdgeExcluder edgeExcluder;
  private final VertexIdTranslate vertexIdTranslate;
  private final RandomWalkPathFollowerCompanion companion;
  private final Object printLock = new Object();

  public RandomWalkPathFollower(GraphOnDisk graph,
                                List<NodePairInstance> instances,
                                Set<Integer> allowedTargets,
                                EdgeExcluder edgeExcluder,
                                List<PathType> paths,
                                int walksPerPath,
                                MatrixRowPolicy companionAcceptPolicy,
                                boolean normalizeWalkProbabilities) {
    this.numWalksPerPath = walksPerPath;
    this.instances = instances;
    sourcesMap = Maps.newHashMap();
    for (NodePairInstance instance : instances) {
      MapUtil.addValueToKeySet(sourcesMap, instance.source(), instance.target());
    }
    try {
      this.drunkardMobEngine = new DrunkardMobEngine<EmptyType, Integer>(
          graph.graphFile(), graph.numShards(), new Factory());
      this.drunkardMobEngine.setEdataConverter(new IntConverter());
    } catch (IOException e) {
      throw new RuntimeException("IOException when creating DrunkardMobEngine", e);
    }
    vertexIdTranslate = this.drunkardMobEngine.getVertexIdTranslate();
    this.edgeExcluder = edgeExcluder;
    edgeExcluder.prepUnallowedWalks(vertexIdTranslate);
    pathTypes = new PathType[paths.size()];
    int pathNum = 0;
    int maxIters = 0;
    for (PathType p : paths) {
      pathTypes[pathNum++] = p;
      maxIters = Math.max(maxIters, p.recommendedIters());
    }
    numPaths = pathNum;
    numIters = maxIters;

    /* Configure walk sources. Note, GraphChi's internal ids are used. */
    List<Integer> sources = new ArrayList<Integer>();
    for (int source : sourcesMap.keySet()) {
      if (source > drunkardMobEngine.getEngine().numVertices()) continue;
      // The training data is a map going from a source to a set of known target instances.
      // We do walks starting from each source, following each of a given set of paths.
      int translatedSource = vertexIdTranslate.forward(source);
      sources.add(translatedSource);
    }

    try {
      companion = new RandomWalkPathFollowerCompanion(graph,
                                                      4,
                                                      Runtime.getRuntime().maxMemory() / 3,
                                                      vertexIdTranslate,
                                                      pathTypes,
                                                      companionAcceptPolicy,
                                                      allowedTargets,
                                                      normalizeWalkProbabilities);
    } catch(RemoteException e) {
      throw new RuntimeException(e);
    }

    Collections.sort(sources);
    DrunkardJob drunkardJob =
        this.drunkardMobEngine.addJob("pathFollower", EdgeDirection.IN_AND_OUT_EDGES, this, companion);
    drunkardJob.configureWalkSources(sources, numWalksPerPath * numPaths);
    sourceIds = drunkardJob.getWalkManager().getSources();
    companion.setSources(sourceIds);
  }

  @Override
  public void execute() {
    logger.info("Creating feature matrix using random walks");
    try {
      drunkardMobEngine.run(numIters);
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public FeatureMatrix getFeatureMatrix() {
    return companion.getFeatureMatrix(instances);
  }

  @Override
  public void shutDown() {
    logger.info("Path Follower shutting down");
    companion.close();
  }

  @Override
  public boolean usesGraphChi() {
    return true;
  }

  @VisibleForTesting
  protected int getWalksPerPath() {
    return numWalksPerPath;
  }

  @VisibleForTesting
  protected RandomWalkPathFollowerCompanion getCompanion() {
    return companion;
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
    Vertex vertex = new Vertex(chiVertex);
    int vertexId = vertex.getId();

    if (vertex.getNumEdges() == 0) {
      // Nothing to do; this means that a walk started at a node that had no connecting edges, so
      // we don't even bother restarting the walks.
      return;
    }

    // Some path types normalize things by the edges going out of a vertex, or do other kinds of
    // expensive computation.  Here we allow the path types to prep for that once, before calling
    // nextHop for each walk.
    PathTypeVertexCache[][] cache = initializePathTypeVertexCaches(vertex, walks);

    // Now we go through the walks.  Basic outline: for each walk, get its path type and hop
    // number, randomly pick an edge that matches the path for the hop number, and advance the
    // walk.
    for (long walk : walks) {
      processSingleWalk(walk, vertex, drunkardContext, random, cache);
    }
  }

  @VisibleForTesting
  protected PathTypeVertexCache[][] initializePathTypeVertexCaches(Vertex vertex, long[] walks) {
    PathTypeVertexCache[][] caches = new PathTypeVertexCache[pathTypes.length][];
    for (long walk : walks) {
      int pathType = Manager.pathType(walk);
      if (caches[pathType] == null) {
        caches[pathType] = new PathTypeVertexCache[pathTypes[pathType].recommendedIters() + 1];
      }
      int hopNum = Manager.hopNum(walk);
      if (caches[pathType][hopNum] == null) {
        caches[pathType][hopNum] = pathTypes[pathType].cacheVertexInformation(vertex, hopNum);
      }
    }
    return caches;
  }

  @VisibleForTesting
  protected void processSingleWalk(long walk,
                                   Vertex vertex,
                                   LongDrunkardContext drunkardContext,
                                   Random random,
                                   PathTypeVertexCache[][] cache) {
    int pathType = Manager.pathType(walk);
    int hopNum = Manager.hopNum(walk);

    /* For debugging
    int sourceV = vertexIdTranslate.backward(sourceIds[staticSourceIdx(walk)]);
    int at = vertexIdTranslate.backward(vertex.getId());
    System.out.println("walk from " + sourceV + " at " + at + " following path " + pathType
    + " hop number " + hopNum);
     */

    boolean trackBit = pathTypes[pathType].isLastHop(hopNum);
    int sourceVertex = sourceIds[staticSourceIdx(walk)];
    int nextVertex = pathTypes[pathType].nextHop(hopNum,
                                                 sourceVertex,
                                                 vertex,
                                                 random,
                                                 edgeExcluder,
                                                 cache[pathType][hopNum]);
    if (nextVertex == -1) {
      resetWalk(walk, drunkardContext);
      return;
    }
    walk = Manager.incrementHopNum(walk);
    drunkardContext.forwardWalkTo(walk, nextVertex, trackBit);
  }

  private void resetWalk(long walk, LongDrunkardContext drunkardContext) {
    walk = Manager.encode(
        Manager.pathType(walk), 0, staticSourceIdx(walk), staticTrackBit(walk), staticOff(walk));
    drunkardContext.resetWalk(walk, false);
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
     * We take the standard (sourceId, track, off) encoding from IntWalkManager and put some walk
     * stuff in front of it - 27 bits for the path type, and 5 bits for the hop number (both of
     * those are longer than we really need, but that's ok).
     *
     * Because we're using the same lower 32 bits, also, we don't have to override the sourceIdx,
     * trackBit, and off methods of the base LongWalkManager, which doesn't use the top 32 bits.
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
      return encode(pathType(walk), hopNum+1, staticSourceIdx(walk), staticTrackBit(walk), staticOff(walk));
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
      // This is assuming that encodeNewWalk is called repeatedly on each source vertex until the
      // correct number of walks is reached.  As of right now, that's how it works, but if things
      // change, this implementation will likely also need to change.
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

  // These next three are for convenience, so that Path.encode doesn't need an instance of Manager.
  // Kind of ugly, but oh well...
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
