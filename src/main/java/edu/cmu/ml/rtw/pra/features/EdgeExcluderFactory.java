package edu.cmu.ml.rtw.pra.features;

import java.util.List;

import edu.cmu.ml.rtw.users.matt.util.Pair;

public interface EdgeExcluderFactory {
  // TODO(matt): this interface might need to be rethought if I ever need a different type of edge
  // excluder, as this is pretty specific to the current SingleEdgeExcluder.
  public EdgeExcluder create(List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude);
}
