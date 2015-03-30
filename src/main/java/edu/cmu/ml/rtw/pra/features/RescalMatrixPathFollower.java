package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import edu.cmu.graphchi.ChiLogger;
import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class RescalMatrixPathFollower implements PathFollower {

  private static final Logger logger = ChiLogger.getLogger("matrix-path-follower");

  private final List<PathType> pathTypes;
  private final String rescalDir;
  private final FileUtil fileUtil;
  private final Dataset data;
  private final Dictionary nodeDict;
  private final Dictionary edgeDict;
  private final Set<Integer> allowedTargets;
  private final int negativesPerSource;

  public RescalMatrixPathFollower(PraConfig config,
                                  List<PathType> pathTypes,
                                  String rescalDir,
                                  Dataset data,
                                  int negativesPerSource) {
    this(config, pathTypes, rescalDir, data, negativesPerSource, new FileUtil());
  }

  @VisibleForTesting
  protected RescalMatrixPathFollower(PraConfig config,
                                     List<PathType> pathTypes,
                                     String rescalDir,
                                     Dataset data,
                                     int negativesPerSource,
                                     FileUtil fileUtil) {
    this.pathTypes = pathTypes;
    this.rescalDir = rescalDir;
    this.data = data;
    this.nodeDict = config.nodeDict;
    this.edgeDict = config.edgeDict;
    if (config.allowedTargets == null) {
      /*
      Set<Integer> allTargets = Sets.newHashSet();
      for (int i = 1; i < nodeDict.getNextIndex(); i++) {
        allTargets.add(i);
      }
      this.allowedTargets = allTargets;
      */
      this.allowedTargets = Sets.newHashSet();
      this.allowedTargets.addAll(data.getAllTargets());
    } else {
      this.allowedTargets = config.allowedTargets;
    }
    this.negativesPerSource = negativesPerSource;
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
        new RescalPathMatrixCreator(pathTypes,
                                    data.getCombinedSourceMap().keySet(),
                                    rescalDir,
                                    nodeDict,
                                    edgeDict,
                                    negativesPerSource,
                                    fileUtil);
    return matrixCreator.getFeatureMatrix(data.getCombinedSourceMap().keySet(), allowedTargets, false);
  }

  @Override
  public boolean usesGraphChi() {
    return false;
  }

  @VisibleForTesting
  public String getRescalDir() {
    return rescalDir;
  }

  @VisibleForTesting
  public int getNegativesPerSource() {
    return negativesPerSource;
  }
}
