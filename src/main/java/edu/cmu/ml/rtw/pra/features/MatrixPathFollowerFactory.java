package edu.cmu.ml.rtw.pra.features;

import java.io.File;
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
    String matrixDir;
    if (config.matrixDir != null) {
      matrixDir = config.matrixDir;
    } else {
      String graphDir = new File(config.graph).getParentFile().getParent() + "/";
      matrixDir = graphDir + "matrices/";
    }
    return new MatrixPathFollower(config.nodeDict.getNextIndex(),
                                  pathTypes,
                                  matrixDir,
                                  data,
                                  config.edgeDict,
                                  config.allowedTargets,
                                  edgesToExclude,
                                  config.maxMatrixFeatureFanOut,
                                  config.normalizeWalkProbabilities);
  }
}
