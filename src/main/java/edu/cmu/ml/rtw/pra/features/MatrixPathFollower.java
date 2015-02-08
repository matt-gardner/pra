package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;

import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class MatrixPathFollower implements PathFollower {

  private final int numNodes;
  private final List<PathType> pathTypes;
  private final String matrixDir;
  private final FileUtil fileUtil;
  private final Dataset data;
  private final Dictionary edgeDict;
  private final Set<Integer> allowedTargets;
  private final List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude;
  private final int maxFanOut;
  private final boolean normalizeWalkProbabilities;

  public MatrixPathFollower(int numNodes,
                            List<PathType> pathTypes,
                            String matrixDir,
                            Dataset data,
                            Dictionary edgeDict,
                            Set<Integer> allowedTargets,
                            List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude,
                            int maxFanOut,
                            boolean normalizeWalkProbabilities) {
    this(numNodes, pathTypes, matrixDir, data, edgeDict, allowedTargets, edgesToExclude, maxFanOut, normalizeWalkProbabilities, new FileUtil());
  }

  @VisibleForTesting
  protected MatrixPathFollower(int numNodes,
                               List<PathType> pathTypes,
                               String matrixDir,
                               Dataset data,
                               Dictionary edgeDict,
                               Set<Integer> allowedTargets,
                               List<Pair<Pair<Integer, Integer>, Integer>> edgesToExclude,
                               int maxFanOut,
                               boolean normalizeWalkProbabilities,
                               FileUtil fileUtil) {
    this.numNodes = numNodes;
    this.pathTypes = pathTypes;
    this.matrixDir = matrixDir;
    this.data = data;
    this.edgeDict = edgeDict;
    this.allowedTargets = allowedTargets;
    this.edgesToExclude = edgesToExclude;
    this.maxFanOut = maxFanOut;
    this.normalizeWalkProbabilities = normalizeWalkProbabilities;
    this.fileUtil = fileUtil;
  }

  // We don't need to do anything with these two.
  @Override
  public void execute() { }
  @Override
  public void shutDown() { }

  @Override
  public FeatureMatrix getFeatureMatrix() {
    PathMatrixCreator matrixCreator =
        new PathMatrixCreator(numNodes,
                              pathTypes,
                              data.getCombinedSourceMap().keySet(),
                              matrixDir,
                              edgeDict,
                              edgesToExclude,
                              maxFanOut,
                              normalizeWalkProbabilities,
                              new FileUtil());
    return matrixCreator.getFeatureMatrix(data.getCombinedSourceMap().keySet(), allowedTargets);
  }

  @Override
  public boolean usesGraphChi() {
    return false;
  }
}
