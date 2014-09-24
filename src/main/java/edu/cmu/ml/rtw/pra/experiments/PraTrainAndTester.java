package edu.cmu.ml.rtw.pra.experiments;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;

import edu.cmu.graphchi.ChiFilenames;
import edu.cmu.graphchi.ChiLogger;
import edu.cmu.graphchi.EmptyType;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.preprocessing.EdgeProcessor;
import edu.cmu.graphchi.preprocessing.FastSharder;
import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.features.FeatureGenerator;
import edu.cmu.ml.rtw.pra.features.FeatureMatrix;
import edu.cmu.ml.rtw.pra.features.PathType;
import edu.cmu.ml.rtw.pra.models.PraModel;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.Pair;

/**
 * Train and test PRA models in a couple of different configurations.
 *
 * @author Matt Gardner (mg1@cs.cmu.edu)
 */
public class PraTrainAndTester {

  private static Logger logger = ChiLogger.getLogger("pra-driver");
  private final FileUtil fileUtil;

  public PraTrainAndTester() {
    this(new FileUtil());
  }

  @VisibleForTesting
  public PraTrainAndTester(FileUtil fileUtil) {
    this.fileUtil = fileUtil;
  }

  /**
   * Given a list of (source, target) pairs (and a lot of other parameters), split them into
   * training and testing and perform cross validation.  In practice this just calls trainAndTest
   * after splitting the data.
   *
   * @param config A {@link PraConfig} object that specifies where to find the graph and training
   *     data, what options to use, and a lot of other things.
   */
  public void crossValidate(PraConfig config) {
    Pair<Dataset, Dataset> splitData = config.allData.splitData(config.percentTraining);
    Dataset trainingData = splitData.getLeft();
    Dataset testingData = splitData.getRight();
    config.outputter.outputSplitFiles(config.outputBase, trainingData, testingData);
    PraConfig.Builder builder = new PraConfig.Builder(config);
    builder.setAllData(null);
    builder.setPercentTraining(0);
    builder.setTrainingData(trainingData);
    builder.setTestingData(testingData);
    trainAndTest(builder.build());
  }

  /**
   * Given a set of training examples and a set of testing examples, train and test a PRA model.
   *
   * @param config A {@link PraConfig} object that specifies where to find the graph and training
   *     data, what options to use, and a lot of other things.
   */
  public void trainAndTest(PraConfig config) {
    List<Pair<PathType, Double>> model = trainPraModel(config);
    Map<Integer, List<Pair<Integer, Double>>> scores = testPraModel(config, model);

  }

  /**
   * Given a set of input data and some other parameters, this code runs three steps:
   * selectPathFeatures, computeFeatureValues, and learnFeatureWeights, returning the results as
   * a list of Pair<PathType, Double> weights.
   *
   * @param config A {@link PraConfig} object that specifies where to find the graph and training
   *     data, what options to use, and a lot of other things.
   *
   * @return A learned model encoded as a list of <code>Pair<PathType, Double></code> objects.
   */
  public List<Pair<PathType, Double>> trainPraModel(PraConfig config) {
    FeatureGenerator generator = new FeatureGenerator(config);
    List<PathType> pathTypes = generator.selectPathFeatures(config.trainingData);
    String matrixOutput = null;  // PraModel outputs split versions of the training matrix
    FeatureMatrix featureMatrix = generator.computeFeatureValues(pathTypes,
                                                                 config.trainingData,
                                                                 matrixOutput);
    PraModel praModel = new PraModel(config);
    List<Double> weights = praModel.learnFeatureWeights(featureMatrix,
                                                        config.trainingData,
                                                        pathTypes);
    List<Pair<PathType, Double>> model = new ArrayList<Pair<PathType, Double>>();
    for (int i=0; i<pathTypes.size(); i++) {
      model.add(new Pair<PathType, Double>(pathTypes.get(i), weights.get(i)));
    }
    return model;
  }

  /**
   * Given a list of Pair<PathType, Double> weights and a set of test source nodes (with,
   * optionally, a set of corresponding test target nodes that should be excluded from walks),
   * return a set of (source, target, score) triples.  We do not output any test results here,
   * leaving that to the caller (but see, for instance, the <code>outputScores</code> method).
   *
   * @param config A {@link PraConfig} object that specifies where to find the graph and training
   *     data, what options to use, and a lot of other things.
   * @param model A list of PathTypes and associated weights that constitutes a trained PRA model.
   *
   * @return A map from source node to (target node, score) pairs, where the score is computed from
   * the features in the feature matrix and the supplied weights.  Note that there may be some
   * sources from the input list that have no corresponding scores.  This means that there were no
   * paths of the input types from the source node to an acceptable target.
   */
  public Map<Integer, List<Pair<Integer, Double>>> testPraModel(
      PraConfig config,
      List<Pair<PathType, Double>> model) {
    List<PathType> pathTypes = new ArrayList<PathType>();
    List<Double> weights = new ArrayList<Double>();
    for (int i=0; i<model.size(); i++) {
      if (model.get(i).getRight() == 0.0) {
        continue;
      }
      pathTypes.add(model.get(i).getLeft());
      weights.add(model.get(i).getRight());
    }
    String output = config.outputBase == null ? null : config.outputBase + "test_matrix.tsv";
    FeatureGenerator generator = new FeatureGenerator(config);
    FeatureMatrix featureMatrix = generator.computeFeatureValues(pathTypes,
                                                                 config.testingData,
                                                                 output);
    PraModel praModel = new PraModel(config);
    Map<Integer, List<Pair<Integer, Double>>> scores =
        praModel.classifyInstances(featureMatrix, weights);
    // TODO(matt): analyze the scores here and output a result to the command line?  At least it
    // might be useful to have a "metrics.tsv" file, or something, that computes some metrics over
    // these scores.
    config.outputter.outputScores(config.outputBase + "scores.tsv", scores, config);
    return scores;
  }
}
