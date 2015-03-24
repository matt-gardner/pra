package edu.cmu.ml.rtw.pra.features;

import java.util.List;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public interface PathFollowerFactory {
  public PathFollower create(List<PathType> pathTypes,
                             PraConfig config,
                             Dataset data,
                             EdgeExcluder edgeExcluder);
}
