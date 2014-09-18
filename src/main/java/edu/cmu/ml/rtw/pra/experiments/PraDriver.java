package edu.cmu.ml.rtw.pra.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import edu.cmu.graphchi.ChiFilenames;
import edu.cmu.graphchi.ChiLogger;
import edu.cmu.graphchi.EmptyType;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.preprocessing.EdgeProcessor;
import edu.cmu.graphchi.preprocessing.FastSharder;
import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.features.FeatureMatrix;
import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy;
import edu.cmu.ml.rtw.pra.features.PathType;
import edu.cmu.ml.rtw.pra.features.PathTypePolicy;
import edu.cmu.ml.rtw.pra.features.FeatureGenerator;
import edu.cmu.ml.rtw.pra.models.PraModel;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.util.Pair;

/**
 * A driver for PRA.
 *
 * There are methods here that will let a caller hook into PRA at pretty much any level you want.
 * There are high-level "split this data into training and test and tell me how I did" methods,
 * medium-level "train (or test) a PRA model on this data" methods, and low-level "select path
 * features" type methods.
 *
 * When used as a main class, this class uses the command line arguments to run one of the
 * high-level methods; i.e., we split the given data into training and test, train a PRA model,
 * test the PRA model, and output results to a file.
 *
 * @author Matt Gardner (mg1@cs.cmu.edu)
 */
public class PraDriver {

  private static Logger logger = ChiLogger.getLogger("pra-driver");

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  //
  // High-level methods: currently just cross validation, and a fixed train/test split.  In the
  // future, I hope to add a few other high-level methods, like a setup where the test split only
  // has source nodes.
  //
  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Given a list of (source, target) pairs (and a lot of other parameters), split them into
   * training and testing and perform cross validation.  In practice this just calls trainAndTest
   * after splitting the data.
   *
   * @param config A {@link PraConfig} object that specifies where to find the graph and training
   *     data, what options to use, and a lot of other things.
   */
  public static void crossValidate(PraConfig config) {
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
  public static void trainAndTest(PraConfig config) {
    List<Integer> trainingSources = config.trainingData.getPositiveSources();
    List<Integer> trainingTargets = config.trainingData.getPositiveTargets();
    List<Integer> testingSources = config.testingData.getPositiveSources();
    List<Integer> testingTargets = config.testingData.getPositiveTargets();
    List<Integer> allSources = new ArrayList<Integer>();
    allSources.addAll(trainingSources);
    allSources.addAll(testingSources);
    List<Integer> allTargets = new ArrayList<Integer>();
    allTargets.addAll(trainingTargets);
    allTargets.addAll(testingTargets);

    List<Pair<PathType, Double>> model = trainPraModel(config);
    Map<Integer, List<Pair<Integer, Double>>> scores = testPraModel(config, model);

  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  //
  // Medium-level methods: training and testing models
  //
  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Given a set of input data and some other parameters, this code runs three steps:
   * selectPathFeatures, computeFeatureValues, and learnFeatureWeights, returning the results as
   * a list of Pair<PathType, Double> weights.
   *
   * @param config A {@link PraConfig} object that specifies where to find the graph and training
   *     data, what options to use, and a lot of other things.
   * @param sources A list of source nodes to use as training data
   * @param targets A list of target nodes corresponding to the input source nodes
   *
   * @return A learned model encoded as a list of <code>Pair<PathType, Double></code> objects.
   */
  public static List<Pair<PathType, Double>> trainPraModel(PraConfig config) {
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
   * @param sources A list of source nodes to use as training data
   * @param targets A list of target nodes corresponding to the input source nodes.  This is used
   *     in two ways: it could be used to determine which rows to return in the feature matrix,
   *     depending on the value of acceptPolicy.  It is also used to restrict walks along edges of
   *     types that are in unallowedEdges.  If this is null, there are no restrictions applied.
   *     This is essentially telling PRA to query (source, relation, *) and give a prediction - you
   *     would want to do this in production, but likely not in testing.
   *
   * @return A map from source node to (target node, score) pairs, where the score is computed from
   * the features in the feature matrix and the supplied weights.  Note that there may be some
   * sources from the input list that have no corresponding scores.  This means that there were no
   * paths of the input types from the source node to an acceptable target.
   */
  public static Map<Integer, List<Pair<Integer, Double>>> testPraModel(
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

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  //
  // Some utility methods that are written for internal use, called by the methods above.  These
  // methods may have some use for outside callers, if you are hooking into the medium-level or
  // low-level methods, but they are not as well documented as the methods above.
  //
  // createInverses takes a file name and produces a map for use as input to trainPraModel and
  // selectPathFeatures.
  //
  // processGraph does the preprocessing (sharding) that must be done to the graph prior to running
  // either trainPraModel or testPraModel.  The high level methods here call it, and you must also
  // call it if you are not calling one of the high level methods.
  //
  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Reads a file containing a mapping between relations and their inverses, and returns the
   * result as a map.  Note that the file should be in terms of edge _indexes_, not edge _names_.
   *
   * @deprecated use {@link KbPraDriver.createInverses} instead
   */
  @Deprecated
  public static Map<Integer, Integer> createInverses(String filename) throws IOException {
    Map<Integer, Integer> inverses = new HashMap<Integer, Integer>();
    BufferedReader reader = new BufferedReader(new FileReader(filename));
    String line;
    while ((line = reader.readLine()) != null) {
      String[] parts = line.split("\t");
      int rel = Integer.parseInt(parts[0]);
      int inv = Integer.parseInt(parts[1]);
      if (!inverses.containsKey(inv)) {
        inverses.put(rel, inv);
      }
    }
    reader.close();
    return inverses;
  }

  /**
   * Runs GraphChi's preprocessing (sharding) on the graph.  This produces a number of shard files,
   * and if the files are already present, this is a no-op.  So it's only run once for each graph,
   * no matter how many times you run GraphChi code.
   */
  public static void processGraph(String baseFilename, int numShards) throws IOException {
    FastSharder sharder = new FastSharder<EmptyType, Integer>(baseFilename, numShards, null,
        new EdgeProcessor<Integer>() {
          public Integer receiveEdge(int from, int to, String token) {
            return Integer.parseInt(token);
          }
        }, null, new IntConverter());
    if (!new File(ChiFilenames.getFilenameIntervals(baseFilename, numShards)).exists()) {
      sharder.shard(new FileInputStream(new File(baseFilename)), "edgelist");
    } else {
      logger.info("Found shards -- no need to pre-process");
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////
  //
  // Main method stuff, including command line processing.
  //
  ////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////


  public static void main(String[] args) throws IOException, InterruptedException {
    Options cmdLineOptions = createOptionParser();
    CommandLine cmdLine = null;
    try {
      CommandLineParser parser = new PosixParser();
      cmdLine =  parser.parse(cmdLineOptions, args);
    } catch(ParseException e) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("PraDriver", cmdLineOptions);
      System.exit(-1);
    }
    runPra(cmdLine);
    // Somewhere in DrunkardMobEngine, the threads aren't exitting properly
    System.exit(0);
  }

  public static Options createOptionParser() {
    Options cmdLineOptions = new Options();
    // Options common to both walks
    cmdLineOptions.addOption("g", "graph", true, "graph file name");
    cmdLineOptions.addOption("n", "nshards", true, "number of shards");
    cmdLineOptions.addOption("t", "targets", true,
                             "_all_ acceptable targets, even those not in the data file");
    cmdLineOptions.addOption(null, "toignore", true, "toIgnore filename");
    cmdLineOptions.addOption(null, "inverses", true, "inverses filename");
    cmdLineOptions.addOption("o", "outdir", true, "base directory for output");
    // Cross-validation vs. train and test options
    // (CV)
    cmdLineOptions.addOption("d", "data", true,
                             "data file name (mutually exclusive with 'training' and 'testing' options)");
    cmdLineOptions.addOption(null, "percenttraining", true,
                             "how much of the data should be used for training (default: .9)");
    // (train and test)
    cmdLineOptions.addOption(null, "training", true, "training data file name");
    cmdLineOptions.addOption(null, "testing", true, "testing data file name");
    // Learning options
    cmdLineOptions.addOption(null, "l2weight", true,
                             "l2 regularization weight to be used for training");
    cmdLineOptions.addOption(null, "onlyexplicitnegatives", true,
                             "use unseen examples as negative when training");
    // Options specific to the path finding walk
    cmdLineOptions.addOption(null, "walkspersource", true,
                             "number of walks to start from each source");
    cmdLineOptions.addOption(null, "niters", true, "number of iterations");
    cmdLineOptions.addOption(null, "pathpolicy", true,
                             "which path types to keep (everything/paired-only)");
    // Options specific to the path following walk
    cmdLineOptions.addOption(null, "numpaths", true, "number of paths to use");
    cmdLineOptions.addOption(null, "walksperpath", true,
                             "number of walks to start from each source per path");
    cmdLineOptions.addOption(null, "acceptpolicy", true,
                             "which matrix rows to keep (everything/all-targets/paired-targets-only)");
    return cmdLineOptions;
  }

  /**
   * @deprecated use {@link KbPraDriver.runPra} instead.  This class has methods to train and
   * test PRA models, but the actual driver code, that reads in options and whatnot, is obsolete.
   * This code doesn't cover all of the options that are available, and it is a pain to use.  So
   * use KbPraDriver instead.
   */
  @Deprecated
  public static void runPra(CommandLine cmdLine) throws IOException, InterruptedException {
    PraConfig.Builder builder = new PraConfig.Builder();
    builder.setGraph(cmdLine.getOptionValue("graph"));
    String outdir = cmdLine.getOptionValue("outdir", null);
    new File(outdir).mkdirs();
    builder.setOutputBase(outdir);
    builder.setNumShards(Integer.parseInt(cmdLine.getOptionValue("nshards")));
    builder.setL2Weight(Double.parseDouble(cmdLine.getOptionValue("l2weight", "50")));
    builder.setWalksPerSource(Integer.parseInt(cmdLine.getOptionValue("walkspersource")));
    builder.setWalksPerPath(Integer.parseInt(cmdLine.getOptionValue("walksperpath")));
    builder.setNumIters(Integer.parseInt(cmdLine.getOptionValue("niters")));
    builder.setNumPaths(Integer.parseInt(cmdLine.getOptionValue("numpaths")));
    builder.setRelationInverses(createInverses(cmdLine.getOptionValue("inverses")));
    if (cmdLine.getOptionValue("onlyexplicitnegatives", "false").equals("true")) {
      builder.onlyExplicitNegatives();
    }

    String acceptPolicyStr = cmdLine.getOptionValue("acceptpolicy");
    if (acceptPolicyStr.equals("everything")) {
      builder.setAcceptPolicy(MatrixRowPolicy.EVERYTHING);
    } else if (acceptPolicyStr.equals("all-targets")) {
      builder.setAcceptPolicy(MatrixRowPolicy.ALL_TARGETS);
    } else if (acceptPolicyStr.equals("paired-targets-only")) {
      builder.setAcceptPolicy(MatrixRowPolicy.PAIRED_TARGETS_ONLY);
    } else {
      throw new RuntimeException("Unrecognized accept policy: " + acceptPolicyStr);
    }

    String pathPolicyStr = cmdLine.getOptionValue("pathpolicy");
    if (pathPolicyStr.equals("everything")) {
      builder.setPathTypePolicy(PathTypePolicy.EVERYTHING);
    } else if (pathPolicyStr.equals("paired-only")) {
      builder.setPathTypePolicy(PathTypePolicy.PAIRED_ONLY);
    } else {
      throw new RuntimeException("Unrecognized path type policy: " + pathPolicyStr);
    }

    boolean doCrossValidation = false;

    // Process the data file
    String dataFile = cmdLine.getOptionValue("data", null);
    if (dataFile != null) {
      builder.setAllData(Dataset.readFromFile(new File(dataFile)));
      builder.setPercentTraining(Double.parseDouble(cmdLine.getOptionValue("percenttraining",
                                                                           ".9")));
      doCrossValidation = true;
    } else {
      builder.setTrainingData(Dataset.readFromFile(cmdLine.getOptionValue("training")));
      builder.setTestingData(Dataset.readFromFile(cmdLine.getOptionValue("testing")));
    }

    BufferedReader reader;
    String line;
    // Process the "targets" file, containing all allowed targets
    Set<Integer> allowedTargets = null;
    String targets_file = cmdLine.getOptionValue("targets", null);
    if (targets_file != null) {
      builder.setAllowedTargets(FileUtil.readIntegerSetFromFile(targets_file));
    }

    // Process the "to ignore" file (containing relations that you can't use between a source
    // node and its corresponding target)
    String toIgnoreFilename = cmdLine.getOptionValue("toignore");
    builder.setUnallowedEdges(FileUtil.readIntegerListFromFile(toIgnoreFilename));

    PraConfig config = builder.build();
    // Make sure the graph is sharded
    processGraph(config.graph, config.numShards);

    // Run PRA
    if (doCrossValidation) {
      crossValidate(config);
    } else {
      trainAndTest(config);
    }
  }
}
