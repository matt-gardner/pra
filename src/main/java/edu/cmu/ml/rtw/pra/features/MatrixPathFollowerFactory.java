package edu.cmu.ml.rtw.pra.features;

import java.util.List;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class MatrixPathFollowerFactory implements PathFollowerFactory {
  @Override
  public PathFollower create(List<PathType> pathTypes,
                             PraConfig config,
                             Dataset data,
                             List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude) {
    return new MatrixPathFollower(config.nodeDict.getNextIndex(),
                                  pathTypes,
                                  config.graph,
                                  data,
                                  config.allowedTargets);
  }
}
