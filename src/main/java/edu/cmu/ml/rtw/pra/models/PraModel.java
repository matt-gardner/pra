package edu.cmu.ml.rtw.pra.models;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.pra.features.FeatureMatrix;
import edu.cmu.ml.rtw.pra.features.MatrixRow;
import edu.cmu.ml.rtw.pra.features.PathType;
import edu.cmu.ml.rtw.users.matt.util.CollectionsUtil;
import edu.cmu.ml.rtw.users.matt.util.Pair;
import edu.cmu.ml.rtw.users.matt.util.PairComparator;

/**
 * Handles learning and classification for a simple logistic regression model that uses PRA
 * features.
 *
 * I thought about spending time to make this class nicer.  But then I decided that what I'm really
 * focusing on is the feature generation side of things, and the point is to use PRA features in
 * different kinds of models.  Spending too much time on making a consistent interface for just a
 * logistic regression model didn't seem to be worth it.  Maybe some day, but not now.  I've
 * thought about doing some experiments where you vary the relation extraction model (like, SVM vs.
 * LR, ranking loss instead of likelihood, different ways to handle negative evidence).  If I ever
 * get to those experiments, I'll clean up this code, but until then, I won't change what isn't
 * broken.
 */
public class PraModel {

  private PraConfig config;
  private static Logger logger = Logger.getLogger("pra-model");

  public PraModel(PraConfig config) {
    this.config = config;
  }

  /**
   * Given a feature matrix and a list of sources and targets that determines whether an
   * instances is positive or negative, train a logistic regression classifier.
   *
   * @param config A {@link PraConfig} object that specifies where to find the graph and training
   *     data, what options to use, and a lot of other things.
   * @param featureMatrix A feature matrix encoded as a list of {@link MatrixRow} objects
   * @param positiveSources A list of source nodes for positive examples.  Rows in the feature
   *     matrix whose key matches a (source, target) pair will be used as positive examples, and
   *     those that don't will be used as negative examples.
   * @param positiveTargets A list of target nodes corresponding to the list of source nodes.  It
   *     might be a good idea to switch to using a list of Pair objects instead of two lists...
   * @param pathTypes The {@link PathType} objects that correspond to the columns in the feature
   *     matrix.  This is used in two ways.  First, it is used to set the Alphabet for the mallet
   *     classifier (really we only need the number of path types for this, as we just use the
   *     index as the feature type).  Second, we use the path type descriptions to output a
   *     more-or-less human-readable description of the learned model.
   *
   * @return A list of learned weights, one for each path type.
   */
  public List<Double> learnFeatureWeights(FeatureMatrix featureMatrix,
                                          Dataset dataset,
                                          List<PathType> pathTypes) {
    logger.info("Learning feature weights");
    logger.info("Prepping training data");
    // We could use a Pair here, but it's just for checking set membership, and String hashing
    // is probably faster.
    Set<String> knownPositives = dataset.getPositiveInstancesAsStrings();
    Set<String> knownNegatives = dataset.getNegativeInstancesAsStrings();
    // Split the matrix into positive and negative examples, based on the input data.
    List<MatrixRow> positiveExamples = new ArrayList<MatrixRow>();
    List<MatrixRow> negativeExamples = new ArrayList<MatrixRow>();
    List<MatrixRow> unseenExamples = new ArrayList<MatrixRow>();
    for (MatrixRow row : featureMatrix.getRows()) {
      String pair = row.sourceNode + " " + row.targetNode;
      if (knownPositives.contains(pair)) {
        positiveExamples.add(row);
      } else if (knownNegatives.contains(pair)) {
        negativeExamples.add(row);
      } else {
        unseenExamples.add(row);
      }
    }
    FeatureMatrix positiveMatrix = new FeatureMatrix(positiveExamples);
    FeatureMatrix negativeMatrix = new FeatureMatrix(negativeExamples);
    FeatureMatrix unseenMatrix = new FeatureMatrix(unseenExamples);
    if (config.outputBase != null) {
      String base = config.outputBase;
      config.outputter.outputFeatureMatrix(base + "positive_matrix.tsv", positiveMatrix, pathTypes);
      config.outputter.outputFeatureMatrix(base + "negative_matrix.tsv", negativeMatrix, pathTypes);
      config.outputter.outputFeatureMatrix(base + "unseen_matrix.tsv", unseenMatrix, pathTypes);
    }
    // Set up some mallet boiler plate so we can use Burr's ShellClassifier
    Pipe pipe = new Noop();
    InstanceList data = new InstanceList(pipe);
    Alphabet alphabet = new Alphabet(pathTypes.toArray());

    int numPositiveFeatures = 0;
    for (MatrixRow positiveExample : positiveMatrix.getRows()) {
      numPositiveFeatures += positiveExample.columns;
      data.addThruPipe(matrixRowToInstance(positiveExample, alphabet, true));
    }
    addNegativeEvidence(positiveMatrix.size(),
                        numPositiveFeatures,
                        negativeMatrix,
                        unseenMatrix,
                        data,
                        alphabet,
                        config);
    MalletLogisticRegression lr = new MalletLogisticRegression(alphabet);
    if (config.l2Weight != 0.0) {
      logger.info("Setting L2 weight to " + config.l2Weight);
      lr.setL2wt(config.l2Weight);
    }
    if (config.l1Weight != 0.0) {
      logger.info("Setting L1 weight to " + config.l1Weight);
      lr.setL1wt(config.l1Weight);
    }
    // Finally, we train.  All that prep and everything that follows is really just to get
    // ready for and pass on the output of this one line.
    logger.info("Training the classifier");
    lr.train(data);
    int[] features = lr.getSparseFeatures();
    double[] params = lr.getSparseParams();
    double bias = lr.getBias();
    List<Double> weights = new ArrayList<Double>();
    int j = 0;
    for (int i=0; i<pathTypes.size(); i++) {
      if (j >= features.length) {
        weights.add(0.0);
      } else if (features[j] > i) {
        weights.add(0.0);
      } else if (features[j] == i) {
        weights.add(params[j]);
        j++;
      }
    }
    logger.info("Outputting feature weights");
    if (config.outputBase != null) {
      config.outputter.outputWeights(config.outputBase + "weights.tsv", weights, pathTypes);
    }
    return weights;
  }

  // TODO(matt): Clean up these three methods.  Probably the right thing to do is to put some
  // kind of class into the PraConfig object that lets you decide how to handle negative
  // evidence.
  public void addNegativeEvidence(int numPositiveExamples,
                                  int numPositiveFeatures,
                                  FeatureMatrix negativeMatrix,
                                  FeatureMatrix unseenMatrix,
                                  InstanceList data,
                                  Alphabet alphabet,
                                  PraConfig config) {
    /*
       sampleUnseenExamples(numPositiveExamples,
       negativeMatrix,
       unseenMatrix,
       data,
       alphabet,
       config);
       */
    weightUnseenExamples(numPositiveFeatures, negativeMatrix, unseenMatrix, data, alphabet, config);
  }

  private void sampleUnseenExamples(int numPositiveExamples,
                                    FeatureMatrix negativeMatrix,
                                    FeatureMatrix unseenMatrix,
                                    InstanceList data,
                                    Alphabet alphabet,
                                    PraConfig config) {
    unseenMatrix.shuffle();
    for (int i = 0; i < numPositiveExamples; i++) {
      data.addThruPipe(matrixRowToInstance(unseenMatrix.getRow(i), alphabet, false));
    }
  }

  private void weightUnseenExamples(int numPositiveFeatures,
                                    FeatureMatrix negativeMatrix,
                                    FeatureMatrix unseenMatrix,
                                    InstanceList data,
                                    Alphabet alphabet,
                                    PraConfig config) {
    int numNegativeFeatures = 0;
    for (MatrixRow negativeExample : negativeMatrix.getRows()) {
      numNegativeFeatures += negativeExample.columns;
      data.addThruPipe(matrixRowToInstance(negativeExample, alphabet, false));
    }
    logger.info("Number of positive features: " + numPositiveFeatures);
    logger.info("Number of negative features: " + numNegativeFeatures);
    if (numNegativeFeatures < numPositiveFeatures && !config.onlyExplicitNegatives) {
      logger.info("Using unseen examples to make up the difference");
      int difference = numPositiveFeatures - numNegativeFeatures;
      int numUnseenFeatures = 0;
      for (MatrixRow unseenExample : unseenMatrix.getRows()) {
        numUnseenFeatures += unseenExample.columns;
      }
      double unseenWeight = difference / (double) numUnseenFeatures;
      logger.info("Unseen weight: " + unseenWeight);
      for (MatrixRow unseenExample : unseenMatrix.getRows()) {
        Instance unseenInstance = matrixRowToInstance(unseenExample, alphabet, false);
        data.addThruPipe(unseenInstance);
        data.setInstanceWeight(unseenInstance, unseenWeight);
      }
    }
  }

  /**
   * Give a score to every row in the feature matrix, according to the given weights.
   *
   * This just applies the logistic function specified by <code>weights</code> to the feature
   * matrix, returning a score for each row in the matrix.  We convert the matrix into a map,
   * keyed by source node, to facilitate easy ranking of predictions for each source.  The lists
   * returned are not sorted yet, however.
   *
   * @param featureMatrix A feature matrix specified as a list of {@link MatrixRow} objects.
   *     Each row receives a score from the logistic function.
   * @param weights A list of feature weights, where the indices to the weights correspond to the
   *     columns of the supplied feature matrix.
   *
   * @return A map from source node to (target node, score) pairs, where the score is computed
   *     from the features in the feature matrix and the supplied weights.
   */
  public Map<Integer, List<Pair<Integer, Double>>> classifyInstances(
      FeatureMatrix featureMatrix, List<Double> weights) {
    Map<Integer, List<Pair<Integer, Double>>> sourceScores =
        new HashMap<Integer, List<Pair<Integer, Double>>>();
    for (MatrixRow row : featureMatrix.getRows()) {
      double score = classifyMatrixRow(row, weights);
      List<Pair<Integer, Double>> scores = sourceScores.get(row.sourceNode);
      if (scores == null) {
        scores = new ArrayList<Pair<Integer, Double>>();
        sourceScores.put(row.sourceNode, scores);
      }
      scores.add(new Pair<Integer, Double>(row.targetNode, score));
    }
    return sourceScores;
  }

  /**
   * File must be in the format "%s\t%f\n", where the string is a path description.  If the model
   * is output by outputWeights, you should be fine.
   */
  public List<Pair<PathType, Double>> readWeightsFromFile(String filename)
      throws IOException {
        List<Pair<PathType, Double>> weights = new ArrayList<Pair<PathType, Double>>();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
          String[] parts = line.split("\t");
          String description = parts[0];
          double weight = Double.parseDouble(parts[1]);
          weights.add(new Pair<PathType, Double>(config.pathTypeFactory.fromString(description),
                                                 weight));
        }
        reader.close();
        return weights;
      }

  public double classifyMatrixRow(MatrixRow row, List<Double> weights) {
    double score = 0.0;
    for (int i=0; i<row.columns; i++) {
      score += row.values[i] * weights.get(row.pathTypes[i]);
    }
    return score;
  }

  public Instance matrixRowToInstance(MatrixRow row, Alphabet alphabet, boolean positive) {
    double value = positive ? 1.0 : 0.0;
    double[] values = row.values.clone();
    if (config.binarizeFeatures) {
      for (int i = 0; i < values.length; i++) {
        if (values[i] > 0) values[i] = 1;
      }
    }
    FeatureVector feature_vector = new FeatureVector(alphabet, row.pathTypes, values);
    return new Instance(feature_vector, value, row.sourceNode + " " + row.targetNode, null);
  }

}
