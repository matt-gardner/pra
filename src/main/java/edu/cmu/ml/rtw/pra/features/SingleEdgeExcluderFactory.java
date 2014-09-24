package edu.cmu.ml.rtw.pra.features;

import java.util.List;

import edu.cmu.ml.rtw.users.matt.util.Pair;

public class SingleEdgeExcluderFactory implements EdgeExcluderFactory {
  public EdgeExcluder create(List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude) {
    return new SingleEdgeExcluder(edgesToExclude);
  }
}
