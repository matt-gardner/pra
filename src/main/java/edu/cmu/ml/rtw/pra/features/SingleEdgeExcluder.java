package edu.cmu.ml.rtw.pra.features;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.ml.rtw.users.matt.util.MapUtil;
import edu.cmu.ml.rtw.util.Pair;

public class SingleEdgeExcluder implements EdgeExcluder {

  // Edges to exclude uses the _graph node ids_ as values in the list.
  private final List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude;
  // unallowedWalks uses the _graphchi internal translated node ids_.  GraphChi makes its own
  // mapping of the node ids to make for more efficient processing.
  private Map<Integer, Map<Integer, List<Integer>>> unallowedWalks;

  public SingleEdgeExcluder(List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude) {
    this.edgesToExclude = edgesToExclude;
  }

  @VisibleForTesting
  protected Map<Integer, Map<Integer, List<Integer>>> getUnallowedWalks() {
    return unallowedWalks;
  }

  @Override
  public void prepUnallowedWalks(VertexIdTranslate vertexIdTranslate) {
    // Set up the edges that should be excluded for easier searching.
    unallowedWalks = Maps.newHashMap();
    for (Pair<Pair<Integer, Integer>, Integer> edge : edgesToExclude) {
      int source = edge.getLeft().getLeft();
      int target = edge.getLeft().getRight();
      int edgeType = edge.getRight();
      int translatedSrc = vertexIdTranslate.forward(source);
      int translatedTarget = vertexIdTranslate.forward(target);
      // Insert (source, (target, edge)) into the map
      MapUtil.addValueToTwoKeyList(unallowedWalks, translatedSrc, translatedTarget, edgeType);
      // Now the other way: (target, (source, edge))
      MapUtil.addValueToTwoKeyList(unallowedWalks, translatedTarget, translatedSrc, edgeType);
    }
  }

  @Override
  public boolean shouldExcludeEdge(int sourceVertex,
                                   int nextVertex,
                                   Vertex currentVertex,
                                   int edgeType) {
    // We can use edges of the relation we're predicting as long as they aren't the instance we're
    // starting the walk from.  So only exclude the walk if the source vertex is the same as the
    // current vertex or the next vertex
    if (sourceVertex != currentVertex.getId() && sourceVertex != nextVertex) return false;
    // Now we look in our unallowedWalks map and see if the edge is there between the current
    // vertex and the next vertex.  It doesn't matter which vertex we use as the source here,
    // because we put both directions into the unallowedWalks map.
    Map<Integer, List<Integer>> sourceMap = unallowedWalks.get(currentVertex.getId());
    if (sourceMap == null) return false;
    List<Integer> unallowedEdges = sourceMap.get(nextVertex);
    if (unallowedEdges == null) return false;
    // If we've made it here, we actually have a (source, target) pair that was given to us in the
    // edgesToExclude parameter.  Now we have to check if the edge we're trying to walk on should
    // be excluded.
    int edgeCount = 0;
    for (int unallowedEdgeType : unallowedEdges) {
      if (unallowedEdgeType == edgeType) {
        edgeCount++;
      }
    }
    // If the edge didn't show up at all, we're good.
    if (edgeCount == 0) return false;
    // But if it did show up, we still might be ok, if there are more edges between the source and
    // target node than just the ones we wanted to exclude.  This shouldn't happen with standard KB
    // edges, but if the edges are embedded into a latent space, there could be other edges that
    // have the same representation, so we have to check the counts here.
    int targetCount = currentVertex.getNodeEdgeCount(nextVertex, edgeType);
    if (edgeCount >= targetCount) return true;
    return false;
  }
}
