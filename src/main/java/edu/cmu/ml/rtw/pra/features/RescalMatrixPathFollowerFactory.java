package edu.cmu.ml.rtw.pra.features;

import java.io.File;
import java.util.List;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class RescalMatrixPathFollowerFactory implements PathFollowerFactory {
  public final String rescalDir;

  public RescalMatrixPathFollowerFactory(String rescalDir) {
    if (rescalDir.endsWith("/")) {
      this.rescalDir = rescalDir;
    } else {
      this.rescalDir = rescalDir + "/";
    }
  }

  @Override
  public PathFollower create(List<PathType> pathTypes,
                             PraConfig config,
                             Dataset data,
                             List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude) {
    return new RescalMatrixPathFollower(config.nodeDict.getNextIndex(),
                                        pathTypes,
                                        rescalDir,
                                        data,
                                        config.nodeDict,
                                        config.edgeDict,
                                        config.allowedTargets,
                                        edgesToExclude);
  }
}
