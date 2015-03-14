package edu.cmu.ml.rtw.pra.features;

import java.io.File;
import java.util.List;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class MatrixPathFollowerFactory implements PathFollowerFactory {
  public final String matrixDir;
  public final int maxFanOut;

  public MatrixPathFollowerFactory(String matrixDir, int maxFanOut) {
    if (matrixDir.endsWith("/")) {
      this.matrixDir = matrixDir;
    } else {
      this.matrixDir = matrixDir + "/";
    }
    this.maxFanOut = maxFanOut;
  }

  @Override
  public PathFollower create(List<PathType> pathTypes,
                             PraConfig config,
                             Dataset data,
                             List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude) {
    String graphDir = new File(config.graph).getParentFile().getParent() + "/";
    return new MatrixPathFollower(config.nodeDict.getNextIndex(),
                                  pathTypes,
                                  graphDir + matrixDir,
                                  data,
                                  config.edgeDict,
                                  config.allowedTargets,
                                  edgesToExclude,
                                  maxFanOut,
                                  config.normalizeWalkProbabilities);
  }
}
