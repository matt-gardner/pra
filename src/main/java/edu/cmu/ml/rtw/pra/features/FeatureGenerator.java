package edu.cmu.ml.rtw.pra.features;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.CollectionsUtil;
import edu.cmu.ml.rtw.users.matt.util.MapUtil;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class FeatureGenerator {
  private PraConfig config;

  public FeatureGenerator(PraConfig config) {
    this.config = config;
  }

  /**
   * Do feature selection for a PRA model, which amounts to finding common paths between sources
   * and targets.
   * <p>
   * This pretty much just wraps around the PathFinder GraphChi program, which does
   * random walks to find paths between source and target nodes, along with a little bit of post
   * processing to (for example) collapse paths that are the same, but are written differently in
   * the GraphChi output because of inverse relationships.
   *
   * @param data A {@link Dataset} containing source and target nodes from which we start walks.
   *
   * @return A ranked list of the <code>numPaths</code> highest ranked path features, encoded as
   *     {@link PathType} objects.
   */
  public List<PathType> selectPathFeatures(Dataset data) {
    List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude = createEdgesToExclude(data);
    PathFinder finder = new PathFinder(config.graph,
                                       config.numShards,
                                       data.getAllSources(),
                                       data.getAllTargets(),
                                       config.edgeExcluderFactory.create(edgesToExclude),
                                       config.walksPerSource,
                                       config.pathTypePolicy,
                                       config.pathTypeFactory);
    finder.execute(config.numIters);
    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    try {
      Thread.sleep(500);
    } catch(InterruptedException e) {
      throw new RuntimeException(e);
    }
    Map<PathType, Integer> pathCounts = finder.getPathCounts();
    finder.shutDown();
    pathCounts = collapseInverses(pathCounts, config.relationInverses);
    config.outputter.outputPathCounts(config.outputBase, "found_path_counts.tsv", pathCounts);
    List<PathType> pathTypes = config.pathTypeSelector.selectPathTypes(pathCounts, config.numPaths);
    config.outputter.outputPaths(config.outputBase, "kept_paths.tsv", pathTypes);
    return pathTypes;
  }

  /**
   * Like selectPathFeatures, but instead of returning just a ranked list of paths, we return
   * all of the paths found between each input (source, target) pair.  This is useful for
   * exploratory data analysis, and maybe a few other things.  If you're building a model where
   * the _presence_ of a connection is important, but not the actual random walk probability,
   * then this is sufficient to give you a feature matrix.  Note that the "count" returned is a
   * bit hackish, however, because the PathFinder joins on intermediate nodes.
   *
   * @param data A {@link Dataset} containing source and target nodes from which we start walks.
   *
   * @return A map from (source, target) pairs to (path type, count) pairs.
   */
  public Map<Pair<Integer, Integer>, Map<PathType, Integer>> findConnectingPaths(Dataset data) {
    List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude = createEdgesToExclude(data);
    PathFinder finder = new PathFinder(config.graph,
                                       config.numShards,
                                       data.getAllSources(),
                                       data.getAllTargets(),
                                       config.edgeExcluderFactory.create(edgesToExclude),
                                       config.walksPerSource,
                                       config.pathTypePolicy,
                                       config.pathTypeFactory);
    finder.execute(config.numIters);
    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    try {
      Thread.sleep(500);
    } catch(InterruptedException e) {
      throw new RuntimeException(e);
    }
    Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCountMap = finder.getPathCountMap();
    finder.shutDown();
    pathCountMap = collapseInversesInCountMap(pathCountMap, config.relationInverses);
    config.outputter.outputPathCountMap(config.outputBase, "path_count_map.tsv", pathCountMap, data);
    return pathCountMap;
  }

  /**
   * Given a set of source nodes and path types, compute values for a feature matrix where the
   * feature types (or columns) are the path types, the rows are (source node, target node)
   * pairs, and the values are the probability of starting at source node, following a path of a
   * particular type, and ending at target node.
   * <p>
   * This is essentially a simple wrapper around the PathFollower GraphChi program, which
   * computes these features using random walks.
   * <p>
   * Note that this computes a fixed number of _columns_ of the feature matrix, with a not
   * necessarily known number of rows (when only the source node of the row is specified).
   *
   * @param pathTypes A list of {@link PathType} objects specifying the path types to follow from
   *     each source node.
   * @param sourcesMap A set of source nodes to start walks from, mapped to targets that are
   *     known to be paired with that source.  The mapped targets have two uses.  First, along
   *     with <code>config.unallowedEdges</code>, they determine which edges are cheating and
   *     thus not allowed to be followed for a given source.  Second, they are used in some
   *     accept policies (see documentation for <code>config.acceptPolicy</code>).  To do a query
   *     of the form (source, relation, *), simply pass in a map that has empty sets
   *     corresponding to each source.  This is appropriate for production use during prediction
   *     time, but not really appropriate for testing, as you could easily be cheating if the
   *     edge you are testing already exists in the graph.  You also shouldn't do this during
   *     training time, as you want to training data to match the test data, and the test data
   *     will not have the relation edge between the source and the possible targets.
   * @param outputFile If not null, the location to save the computed feature matrix.  We can't
   *     just use config.outputBase here, because this method gets called from several places,
   *     with potentially different filenames under config.outputBase.
   *
   * @return A feature matrix encoded as a list of {@link MatrixRow} objects.  Note that this
   *     feature matrix may not have rows corresponding to every source in sourcesMap; if there
   *     were no paths from a source to an acceptable target following any of the path types,
   *     there will be no row in the matrix for that source.
   */
  public FeatureMatrix computeFeatureValues(List<PathType> pathTypes, Dataset data, String outputFile) {
    List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude = createEdgesToExclude(data);
    PathFollower follower = new PathFollower(config.graph,
                                             config.numShards,
                                             data.getCombinedSourceMap(),
                                             config.allowedTargets,
                                             config.edgeExcluderFactory.create(edgesToExclude),
                                             pathTypes,
                                             config.walksPerPath,
                                             config.acceptPolicy,
                                             config.normalizeWalkProbabilities);
    follower.execute();
    // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
    // reason I don't understand.
    try {
      Thread.sleep(1000);
    } catch(InterruptedException e) {
      throw new RuntimeException(e);
    }
    FeatureMatrix featureMatrix = follower.getFeatureMatrix();
    follower.shutDown();
    if (outputFile != null) {
      config.outputter.outputFeatureMatrix(outputFile, featureMatrix, pathTypes);
    }
    return featureMatrix;
  }

  @VisibleForTesting
  protected Map<PathType, Integer> collapseInverses(Map<PathType, Integer> pathCounts,
                                                    Map<Integer, Integer> inverses) {
    Map<PathType, Integer> newCounts = new HashMap<PathType, Integer>();
    for (PathType path : pathCounts.keySet()) {
      PathType collapsed = config.pathTypeFactory.collapseEdgeInverses(path, inverses);
      MapUtil.incrementCount(newCounts, collapsed, pathCounts.get(path));
    }
    return newCounts;
  }

  @VisibleForTesting
  protected Map<Pair<Integer, Integer>, Map<PathType, Integer>> collapseInversesInCountMap(
      Map<Pair<Integer, Integer>, Map<PathType, Integer>> pathCountMap,
      Map<Integer, Integer> inverses) {
    Map<Pair<Integer, Integer>, Map<PathType, Integer>> newPathCountMap = Maps.newHashMap();
    for (Pair<Integer, Integer> pair : pathCountMap.keySet()) {
      Map<PathType, Integer> pathCounts = pathCountMap.get(pair);
      Map<PathType, Integer> map = Maps.newHashMap();
      for (PathType path : pathCounts.keySet()) {
        PathType collapsed = config.pathTypeFactory.collapseEdgeInverses(path, inverses);
        MapUtil.incrementCount(map, collapsed, pathCounts.get(path));
      }
      newPathCountMap.put(pair, map);
    }
    return newPathCountMap;
  }

  @VisibleForTesting
  protected List<Pair<Pair<Integer, Integer>, Integer>> createEdgesToExclude(
      Dataset data) {
    List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude =
        new ArrayList<Pair<Pair<Integer, Integer>, Integer>>();
    // If there was no input data (e.g., if we are actually trying to predict new edges, not
    // just hide edges from ourselves to try to recover), then there aren't any edges to
    // exclude.  So return an empty list.
    if (data == null) {
      return edgesToExclude;
    }
    List<Integer> sources = data.getAllSources();
    List<Integer> targets = data.getAllTargets();
    if (sources.size() == 0 || targets.size() == 0) {
      return edgesToExclude;
    }
    List<Pair<Integer, Integer>> dataPoints = CollectionsUtil.zipLists(data.getAllSources(),
                                                                       data.getAllTargets());
    for (Pair<Integer, Integer> dataPoint : dataPoints) {
      for (int edgeType : config.unallowedEdges) {
        edgesToExclude.add(new Pair<Pair<Integer, Integer>, Integer>(dataPoint, edgeType));
      }
    }
    return edgesToExclude;
  }
}
