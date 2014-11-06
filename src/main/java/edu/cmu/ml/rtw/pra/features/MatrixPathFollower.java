package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;

import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;

public class MatrixPathFollower implements PathFollower {

  private final int numNodes;
  private final List<PathType> pathTypes;
  private final String graphDir;
  private final FileUtil fileUtil;
  private final Dataset data;
  private final Dictionary edgeDict;
  private final Set<Integer> allowedTargets;

  public MatrixPathFollower(int numNodes,
                            List<PathType> pathTypes,
                            String graphDir,
                            Dataset data,
                            Dictionary edgeDict,
                            Set<Integer> allowedTargets) {
    this(numNodes, pathTypes, graphDir, data, edgeDict, allowedTargets, new FileUtil());
  }

  @VisibleForTesting
  protected MatrixPathFollower(int numNodes,
                               List<PathType> pathTypes,
                               String graphDir,
                               Dataset data,
                               Dictionary edgeDict,
                               Set<Integer> allowedTargets,
                               FileUtil fileUtil) {
    this.numNodes = numNodes;
    this.pathTypes = pathTypes;
    this.graphDir = graphDir;
    this.fileUtil = fileUtil;
    this.data = data;
    this.edgeDict = edgeDict;
    this.allowedTargets = allowedTargets;
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
                              graphDir,
                              edgeDict,
                              new FileUtil());
    return matrixCreator.getFeatureMatrix(data.getCombinedSourceMap().keySet(), allowedTargets);
  }

  @Override
  public boolean usesGraphChi() {
    return false;
  }
}
