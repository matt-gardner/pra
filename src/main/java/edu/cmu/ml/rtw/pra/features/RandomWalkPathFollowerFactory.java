package edu.cmu.ml.rtw.pra.features;

import java.util.List;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class RandomWalkPathFollowerFactory implements PathFollowerFactory {
  @Override
  public PathFollower create(List<PathType> pathTypes,
                             PraConfig config,
                             Dataset data,
                             EdgeExcluder edgeExcluder) {
    return new RandomWalkPathFollower(config.graph,
                                      config.numShards,
                                      data.getCombinedSourceMap(),
                                      config.allowedTargets,
                                      edgeExcluder,
                                      pathTypes,
                                      config.walksPerPath,
                                      config.acceptPolicy,
                                      config.normalizeWalkProbabilities);
  }
}
