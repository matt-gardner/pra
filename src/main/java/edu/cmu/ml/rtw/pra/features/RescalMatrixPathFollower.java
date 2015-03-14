package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.ChiLogger;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class RescalMatrixPathFollower implements PathFollower {

  private static final Logger logger = ChiLogger.getLogger("matrix-path-follower");

  private final int numNodes;
  private final List<PathType> pathTypes;
  private final String rescalDir;
  private final FileUtil fileUtil;
  private final Dataset data;
  private final Dictionary nodeDict;
  private final Dictionary edgeDict;
  private final Set<Integer> allowedTargets;

  public RescalMatrixPathFollower(int numNodes,
                                  List<PathType> pathTypes,
                                  String rescalDir,
                                  Dataset data,
                                  Dictionary nodeDict,
                                  Dictionary edgeDict,
                                  Set<Integer> allowedTargets) {
    this(numNodes, pathTypes, rescalDir, data, nodeDict, edgeDict, allowedTargets, new FileUtil());
  }

  @VisibleForTesting
  protected RescalMatrixPathFollower(int numNodes,
                                     List<PathType> pathTypes,
                                     String rescalDir,
                                     Dataset data,
                                     Dictionary nodeDict,
                                     Dictionary edgeDict,
                                     Set<Integer> allowedTargets,
                                     FileUtil fileUtil) {
    this.numNodes = numNodes;
    this.pathTypes = pathTypes;
    this.rescalDir = rescalDir;
    this.data = data;
    this.nodeDict = nodeDict;
    this.edgeDict = edgeDict;
    if (allowedTargets == null) {
      // TOOD(matt): this isn't really the best thing to do, but it will work for now.
      Set<Integer> allTargets = Sets.newHashSet();
      for (int i = 1; i < nodeDict.getNextIndex(); i++) {
        allTargets.add(i);
      }
      this.allowedTargets = allTargets;
    } else {
      this.allowedTargets = allowedTargets;
    }
    this.fileUtil = fileUtil;
  }

  // We don't need to do anything with these two.
  @Override
  public void execute() { }
  @Override
  public void shutDown() { }

  @Override
  public FeatureMatrix getFeatureMatrix() {
    logger.info("Creating feature matrix with matrix multiplication");
    RescalPathMatrixCreator matrixCreator =
        new RescalPathMatrixCreator(numNodes,
                                    pathTypes,
                                    data.getCombinedSourceMap().keySet(),
                                    rescalDir,
                                    nodeDict,
                                    edgeDict,
                                    fileUtil);
    return matrixCreator.getFeatureMatrix(data.getCombinedSourceMap().keySet(), allowedTargets);
  }

  @Override
  public boolean usesGraphChi() {
    return false;
  }
}
