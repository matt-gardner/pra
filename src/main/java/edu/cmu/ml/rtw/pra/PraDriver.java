package edu.cmu.ml.rtw.pra;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import edu.cmu.graphchi.ChiFilenames;
import edu.cmu.graphchi.ChiLogger;
import edu.cmu.graphchi.EmptyType;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.preprocessing.EdgeProcessor;
import edu.cmu.graphchi.preprocessing.FastSharder;
import edu.cmu.ml.rtw.shell.classify.ShellLogReg;
import edu.cmu.ml.rtw.users.matt.util.CollectionsUtil;
import edu.cmu.ml.rtw.util.Dictionary;
import edu.cmu.ml.rtw.util.Pair;
import edu.cmu.ml.rtw.util.PairComparator;

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
 * TODO: There are some really long parameter lists to most of these methods.  It might be worth
 * creating some kind of TrainingConfig and TestingConfig objects that make this a little more
 * sane.
 *
 * @author Matt Gardner (mg1@cs.cmu.edu)
 */
public class PraDriver {

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
        if (config.outputBase != null) {
            try {
                FileWriter writer = new FileWriter(config.outputBase
                                                   + "training_positive_examples.tsv");
                for (Pair<Integer, Integer> pair : trainingData.getPositiveInstances()) {
                    writer.write(pair.getLeft() + "\t" + pair.getRight() + "\n");
                }
                writer.close();
                writer = new FileWriter(config.outputBase + "training_negative_examples.tsv");
                for (Pair<Integer, Integer> pair : trainingData.getNegativeInstances()) {
                    writer.write(pair.getLeft() + "\t" + pair.getRight() + "\n");
                }
                writer.close();
                writer = new FileWriter(config.outputBase + "testing_positive_examples.tsv");
                for (Pair<Integer, Integer> pair : testingData.getPositiveInstances()) {
                    writer.write(pair.getLeft() + "\t" + pair.getRight() + "\n");
                }
                writer.close();
                writer = new FileWriter(config.outputBase + "testing_negative_examples.tsv");
                for (Pair<Integer, Integer> pair : testingData.getNegativeInstances()) {
                    writer.write(pair.getLeft() + "\t" + pair.getRight() + "\n");
                }
                writer.close();
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
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

        try {
            outputScores(config.outputBase + "scores.tsv",
                         scores,
                         config);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
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
        List<PathType> pathTypes = selectPathFeatures(config, config.trainingData);
        String matrixOutput = config.outputBase == null ? null : config.outputBase + "matrix.tsv";
        List<MatrixRow> featureMatrix = computeFeatureValues(config,
                                                             pathTypes,
                                                             config.trainingData,
                                                             matrixOutput);
        List<Double> weights = learnFeatureWeights(config,
                                                   featureMatrix,
                                                   config.trainingData,
                                                   pathTypes);
        List<Pair<PathType, Double>> model = new ArrayList<Pair<PathType, Double>>();
        // TODO: either here or in the testing step, remove path types that got a zero (or
        // near-zero) weight.  This will save us from doing needless computation.
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
     *     depending on the value of acceptPolicy.  It is also used to restrict walks along edges
     *     of types that are in unallowedEdges.  If this is null, there are no restrictions
     *     applied.  This is essentially telling PRA to query (source, relation, *) and give a
     *     prediction - you would want to do this in production, but likely not in testing.
     *
     * @return A map from source node to (target node, score) pairs, where the score is computed
     *     from the features in the feature matrix and the supplied weights.  Note that there may
     *     be some sources from the input list that have no corresponding scores.  This means that
     *     there were no paths of the input types from the source node to an acceptable target.
     */
    public static Map<Integer, List<Pair<Integer, Double>>> testPraModel(
            PraConfig config,
            List<Pair<PathType, Double>> model) {
        List<PathType> pathTypes = new ArrayList<PathType>();
        List<Double> weights = new ArrayList<Double>();
        for (int i=0; i<model.size(); i++) {
            pathTypes.add(model.get(i).getLeft());
            weights.add(model.get(i).getRight());
        }
        String output = config.outputBase == null ? null : config.outputBase + "test_matrix.tsv";
        List<MatrixRow> featureMatrix = computeFeatureValues(config,
                                                             pathTypes,
                                                             config.testingData,
                                                             output);
        return classifyInstances(featureMatrix, weights);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Low-level methods: feature selection and computation, learning, and classification
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Do feature selection for a PRA model, which amounts to finding common paths between sources
     * and targets.
     * <p>
     * This pretty much just wraps around the PathFinder GraphChi program, which does
     * random walks to find paths between source and target nodes, along with a little bit of post
     * processing to (for example) collapse paths that are the same, but are written differently in
     * the GraphChi output between of inverse relationships.
     *
     * @param config A {@link PraConfig} object that specifies where to find the graph and training
     *     data, what options to use, and a lot of other things.
     * @param sources A list of source nodes (we find paths that start from any of these nodes)
     * @param targets A list of target nodes (we find paths that end at any of these nodes)
     *
     * @return A ranked list of the <code>numPaths</code> highest ranked path features, encoded as
     *     {@link PathType} objects.
     */
    public static List<PathType> selectPathFeatures(
            PraConfig config,
            Dataset data) {
        // TODO(matt): put the PathEncoder into PraConfig.
        PathFinder finder = new PathFinder(config.graph,
                                           config.numShards,
                                           data.getAllSources(),
                                           data.getAllTargets(),
                                           config.unallowedEdges,
                                           config.walksPerSource,
                                           config.pathTypePolicy,
                                           new BasicRelationPathEncoder());
        finder.execute(config.numIters);
        // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
        // reason I don't understand.
        try {
            Thread.sleep(500);
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.info("Done executing PathFinder, getting path counts");
        Map<String, Integer> paths = finder.getPathCounts();
        finder.shutDown();
        paths = collapseInverses(paths, config.relationInverses);
        if (config.outputBase != null) {
            try {
                outputPaths(config.outputBase + "paths.tsv", paths);
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
        List<PathType> pathTypes = selectPaths(paths, config.numPaths);
        return pathTypes;
    }

    /**
     * Given a set of source nodes and path types, compute values for a feature matrix where the
     * feature types (or columns) are the path types, the rows are (source node, target node)
     * pairs, and the values are the probability of starting at source node, following a path of a
     * particular type, and ending at target node.
     * <p>
     * This is essentially a simple wrapper around the PathFollower GraphChi program, which
     * computes these features using random walks.
     * <p>
     * Note that this computes a fixed number of _columns_ of the feature matrix, with a not
     * necessarily known number of rows (when only the source node of the row is specified).  To
     * compute a _row_ of the feature matrix (or set of rows), without specifying which columns to
     * compute, use computeFeatureRows instead.
     *
     * @param config A {@link PraConfig} object that specifies where to find the graph and training
     *     data, what options to use, and a lot of other things.
     * @param pathTypes A list of {@link PathType} objects specifying the path types to follow from
     *     each source node.
     * @param sourcesMap A set of source nodes to start walks from, mapped to targets that are
     *     known to be paired with that source.  The mapped targets have two uses.  First, along
     *     with <code>config.unallowedEdges</code>, they determine which edges are cheating and
     *     thus not allowed to be followed for a given source.  Second, they are used in some
     *     accept policies (see documentation for <code>config.acceptPolicy</code>).  To do a query
     *     of the form (source, relation, *), simply pass in a map that has empty sets
     *     corresponding to each source.  This is appropriate for production use during prediction
     *     time, but not really appropriate for testing, as you could easily be cheating if the
     *     edge you are testing already exists in the graph.  You also shouldn't do this during
     *     training time, as you want to training data to match the test data, and the test data
     *     will not have the relation edge between the source and the possible targets.
     * @param outputFile If not null, the location to save the computed feature matrix.  We can't
     *     just use config.outputBase here, because this method gets called from several places,
     *     with potentially different filenames under config.outputBase.
     *
     * @return A feature matrix encoded as a list of {@link MatrixRow} objects.  Note that this
     *     feature matrix may not have rows corresponding to every source in sourcesMap; if there
     *     were no paths from a source to an acceptable target following any of the path types,
     *     there will be no row in the matrix for that source.
     */
    public static List<MatrixRow> computeFeatureValues(
            PraConfig config,
            List<PathType> pathTypes,
            Dataset data,
            String outputFile) {
        PathFollower follower = new PathFollower(config.graph,
                                                 config.numShards,
                                                 data.getCombinedSourceMap(),
                                                 config.allowedTargets,
                                                 config.unallowedEdges,
                                                 pathTypes,
                                                 config.walksPerPath,
                                                 config.acceptPolicy);
        follower.execute();
        // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
        // reason I don't understand.
        try {
            Thread.sleep(1000);
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.info("Done executing PathFollower; getting the feature matrix");
        List<MatrixRow> featureMatrix = follower.getFeatureMatrix();
        follower.shutDown();
        if (outputFile != null) {
            try {
                outputFeatureMatrix(outputFile, featureMatrix);
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
        return featureMatrix;
    }

    /**
     * Given a set of source nodes, compute a set of rows for a feature matrix where the feature
     * types (or columns) are (unspecified) path types, the rows are (source node, target node)
     * pairs, and the values are the probability of starting at source node, following a path of a
     * particular type, and ending at target node.
     * <p>
     * NOTE: After some initial experimentation, I've decided that this is not a very good method
     * for finding the (source, path type, target) features of computeFeatureValues.  The problem
     * is that there are some path types that are _very_ unlikely to be followed when doing an
     * unconstrained walk, but that are important features in a learned PRA model.  So, really, you
     * should only use this method if you _really_ know what you're doing.  It might be useful for
     * category prediction, in the future, but we'll see.
     * <p>
     * This is essentially a simple wrapper around the PathFinder GraphChi program, which
     * does random walks starting from a set of source nodes.  Instead of calling
     * PathFinder.getPathCounts(), as in selectPathFeatures, we call PathFinder.getFeatureMatrix().
     * This is likely a worse approximation to the feature values than using PathFollower, as we're
     * not specifically following each path type (column) with a set of walks.  But this is a way
     * to get a rough approximation of everything involved with a small set of nodes, as we can
     * more efficiently compute all relevant features for any previously-learned model in one go.
     *
     * @param graph Path to the graph file to use for the random walks
     * @param numShards The number of shards that the graph is (or should be) sharded into
     * @param walksPerSource The number of walks to start for each source node.  This, along with
     *     the number of iterations and the number of sources supplied, determines the running time
     *     of this part of the code.  Note that this should be as large as tolerable, to get a
     *     decent approximation for each feature value.
     * @param iterations The number of iterations of GraphChi to run.  Basically you should set
     *     this to the maximum path length you want to consider (there's a random restart
     *     probability in the walk, so you could make the iterations higher to get more walks, but
     *     you should probably just increase walksPerSource, unless you're hitting the upper limit
     *     on the number of walks that GraphChi can handle, which is unlikely for this method).
     * @param sources A set of source nodes to start walks from.
     * @param outputFile If not null, a filename where we should save the feature matrix for later
     *     inspection.  If null, we do not save anything.
     *
     * @return A feature matrix encoded as a list of {@link MatrixRow} objects.  Because the
     *     columns of the matrix aren't specified, and MatrixRow just contains column ids, we also
     *     return a dictionary that maps column ids to path type strings (and vice versa).
     */
    public static Pair<Dictionary, List<MatrixRow>> computeFeatureRows(
            String graph,
            int numShards,
            int walksPerSource,
            int iterations,
            List<Integer> sources,
            String outputFile) {
        PathFinder finder = new PathFinder(graph,
                                           numShards,
                                           sources,
                                           null,
                                           new HashSet<Integer>(),
                                           walksPerSource,
                                           PathTypePolicy.EVERYTHING,  // doesn't actually matter
                                           new BasicRelationPathEncoder());
        finder.execute(iterations);
        // This seems to be necessary on small graphs, at least, and maybe larger graphs, for some
        // reason I don't understand.
        try {
            Thread.sleep(1000);
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.info("Done executing PathFinder; getting the feature matrix");
        Pair<Dictionary, List<MatrixRow>> featureMatrix = finder.getFeatureMatrix();
        finder.shutDown();
        if (outputFile != null) {
            try {
                outputFeatureMatrix(outputFile, featureMatrix.getRight());
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
        return featureMatrix;
    }

    /**
     * Given a feature matrix and a list of sources and targets that determines whether an
     * instances is positive or negative, train a logistic regression classifier.  We use
     * shell.classify.ShellLogReg as our classifier, which is basically just a fancy interface to
     * mallet.
     *
     * TODO: there is currently no way to specify explicit negative examples.  That's not strictly
     * necessary, because the negative examples can be passed in to the feature matrix computation,
     * to ensure that their rows are present, while only passing the positive instances to this
     * method for learning.  It may be worth it, however, to give a nicer interface for specifying
     * negative examples (so that, for instance, some negative examples are guaranteed to be used,
     * bypassing the subsampling that I do).  I'm just not sure at the moment what that interface
     * would look like.
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
    public static List<Double> learnFeatureWeights(
            PraConfig config,
            List<MatrixRow> featureMatrix,
            Dataset dataset,
            List<PathType> pathTypes) {
        logger.info("Learning feature weights");
        logger.info("Prepping training data");
        // We could use a Pair here, but it's just for checking set membership, and String hashing
        // is probably faster.
        Set<String> knownPositives = dataset.getPositiveInstancesAsStrings();
        Set<String> knownNegatives = dataset.getNegativeInstancesAsStrings();
        // Split the matrix into positive and negative examples, based on the input data; this
        // could be made a little more friendly to inputs that include explicit negative examples
        List<MatrixRow> positiveExamples = new ArrayList<MatrixRow>();
        List<MatrixRow> negativeExamples = new ArrayList<MatrixRow>();
        List<MatrixRow> unseenExamples = new ArrayList<MatrixRow>();
        for (MatrixRow row : featureMatrix) {
            String pair = row.sourceNode + " " + row.targetNode;
            if (knownPositives.contains(pair)) {
                positiveExamples.add(row);
            } else if (knownNegatives.contains(pair)) {
                negativeExamples.add(row);
            } else {
                unseenExamples.add(row);
            }
        }
        if (config.useUnseenAsNegative) {
            negativeExamples.addAll(unseenExamples);
        }
        if (config.outputBase != null) {
            try {
                outputFeatureMatrix(config.outputBase + "positive_matrix.tsv", positiveExamples);
                outputFeatureMatrix(config.outputBase + "negative_matrix.tsv", negativeExamples);
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
        // Set up some mallet boiler plate so we can use Burr's ShellClassifier
        Pipe pipe = new Noop();
        InstanceList data = new InstanceList(pipe);
        Alphabet alphabet = new Alphabet(pathTypes.toArray());

        for (MatrixRow row : positiveExamples) {
            data.addThruPipe(matrixRowToInstance(row, alphabet, true));
        }
        // Weight negative examples such that we have an even weight between positive and negative.
        // TODO(matt): allow the caller to specify what the ratio of weight should be, by adding a
        // field to PraConfig.
        double negativeWeight = positiveExamples.size() / (double) negativeExamples.size();
        System.out.println("Negative weight: " + negativeWeight);
        for (MatrixRow negativeExample : negativeExamples) {
            Instance negativeInstance = matrixRowToInstance(negativeExample, alphabet, false);
            data.addThruPipe(negativeInstance);
            data.setInstanceWeight(negativeInstance, negativeWeight);
        }
        ShellLogReg lr = new ShellLogReg(alphabet);
        if (config.l2Weight != 0.0) {
            System.out.println("Setting L2 weight to " + config.l2Weight);
            lr.setL2wt(config.l2Weight);
        }
        if (config.l1Weight != 0.0) {
            System.out.println("Setting L1 weight to " + config.l1Weight);
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
            try {
                outputWeights(config.outputBase + "weights.tsv", weights, pathTypes);
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        }
        return weights;
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
    public static Map<Integer, List<Pair<Integer, Double>>> classifyInstances(
            List<MatrixRow> featureMatrix, List<Double> weights) {
        Map<Integer, List<Pair<Integer, Double>>> sourceScores =
            new HashMap<Integer, List<Pair<Integer, Double>>>();
        for (MatrixRow row : featureMatrix) {
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
     * Output a file containing the supplied scores.  We do this output separate from
     * classifyInstances because it's pretty involved - the other methods just output intermediate
     * results, this actually looks at the training and testing data to see if any particular row
     * is a positive example or not.
     * <p>
     * We take as input all three source maps, in addition to the source scores, because they all
     * have useful information for this.
     *
     * @param filename Place to write the output file
     * @param sourceScores The set of scores for each of the sources
     * @param testingSourcesMap This map is used only to iterate over its keys.  We need to know
     *     which sources were used as test sources, so that we can get an accurate picture of
     *     performance.  We can't just use sourceScores for that, because it may be missing sources
     *     that did not have any paths to a target.  This parameter gives us the complete list, so
     *     we can mark source nodes that had no predictions.
     * @param allSourcesMap This map gives us a list of all known (source, target) instances for a
     *     particular source, including training and testing data.  This way we can see if we
     *     ranked positive instances above (presumed) negative instances.
     * @param trainingSourcesMap This map gives us the list of (source, target) pairs that were
     *     used as training data.  This way we can distinguish in the output between correct
     *     instances that we trained on and correct answers that were hidden and we predicted
     *     anyway.
     */
    public static void outputScores(String filename,
            Map<Integer, List<Pair<Integer, Double>>> sourceScores,
            PraConfig config) throws IOException {
        Map<Integer, Set<Integer>> trainingSourcesMap = config.trainingData.getPositiveSourceMap();
        Map<Integer, Set<Integer>> testingSourcesMap = config.testingData.getPositiveSourceMap();
        Map<Integer, Set<Integer>> allSourcesMap =
                CollectionsUtil.combineMapSets(trainingSourcesMap, testingSourcesMap);
        FileWriter writer = new FileWriter(filename);
        for (int source : testingSourcesMap.keySet()) {
            List<Pair<Integer, Double>> scores = sourceScores.get(source);
            if (scores == null) {
                writer.write(source + "\t\t\t\n\n");
                continue;
            }
            Collections.sort(scores,
                    new PairComparator<Integer, Double>(PairComparator.Side.NEGRIGHT));
            Set<Integer> targetSet = allSourcesMap.get(source);
            if (targetSet == null) {
                targetSet = new HashSet<Integer>();
            }
            Set<Integer> trainingTargetSet = trainingSourcesMap.get(source);
            if (trainingTargetSet == null) {
                trainingTargetSet = new HashSet<Integer>();
            }
            for (Pair<Integer, Double> pair : scores) {
                writer.write(source + "\t" + pair.getLeft() + "\t" + pair.getRight() + "\t");
                if (targetSet.contains(pair.getLeft().intValue())) {
                    writer.write("*");
                }
                if (trainingTargetSet.contains(pair.getLeft().intValue())) {
                    writer.write("^");
                }
                writer.write("\n");
            }
            writer.write("\n");
        }
        writer.close();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Some utility methods that are written for internal use, called by the methods above.  These
    // methods may have some use for outside callers, if you are hooking into the medium-level or
    // low-level methods, but they are not as well documented as the methods above.
    //
    // createInverses takes a file name and produces a map for use as input to
    // trainPraModel and selectPathFeatures.
    //
    // processGraph does the preprocessing (sharding) that must be done to the graph prior to
    // running either trainPraModel or testPraModel.  The high level methods here call it, and you
    // must also call it if you are not calling one of the high level methods.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Reads a file containing a mapping between relations and their inverses, and returns the
     * result as a map.  Note that the file should be in terms of edge _indexes_, not edge _names_.
     */
    public static Map<String, String> createInverses(String filename) throws IOException {
        Map<String, String> inverses = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\t");
            if (!inverses.containsKey(parts[1])) {
                inverses.put(parts[0], parts[1]);
            }
        }
        reader.close();
        return inverses;
    }

    /**
     * Runs GraphChi's preprocessing (sharding) on the graph.  This produces a number of shard
     * files, and if the files are already present, this is a no-op.  So it's only run once for
     * each graph, no matter how many times you run GraphChi code.
     */
    public static void processGraph(String baseFilename, int numShards)
            throws IOException {
        FastSharder sharder = createSharder(baseFilename, numShards);
        if (!new File(ChiFilenames.getFilenameIntervals(baseFilename, numShards)).exists()) {
            sharder.shard(new FileInputStream(new File(baseFilename)), "edgelist");
        } else {
            logger.info("Found shards -- no need to pre-process");
        }
    }

    /**
     * File must be in the format "%s\t%f\n", where the string is a path description.  If the model
     * is output by outputWeights, you should be fine.
     */
    public static List<Pair<PathType, Double>> readWeightsFromFile(String filename)
            throws IOException {
        List<Pair<PathType, Double>> weights = new ArrayList<Pair<PathType, Double>>();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\t");
            String pathDescription = parts[0];
            double weight = Double.parseDouble(parts[1]);
            weights.add(new Pair<PathType, Double>(new PathType(pathDescription), weight));
        }
        reader.close();
        return weights;
    }

    public static Set<Integer> readIntegerSetFromFile(String filename) throws IOException {
        Set<Integer> integers = new HashSet<Integer>();
        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        String line;
        while ((line = reader.readLine()) != null) {
            integers.add(Integer.parseInt(line));
        }
        reader.close();
        return integers;
    }

    public static double classifyMatrixRow(MatrixRow row, List<Double> weights) {
        double score = 0.0;
        for (int i=0; i<row.columns; i++) {
            score += row.values[i] * weights.get(row.pathTypes[i]);
        }
        return score;
    }

    public static void translateExampleFile(String exampleFilename,
                                            Dictionary nodeDict) {
        String outFilename = exampleFilename + ".translated";
        try {
            FileWriter writer = new FileWriter(outFilename);
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(exampleFilename));
            while ((line = reader.readLine()) != null) {
                String[] lineFields = line.split("\t");
                String source = lineFields[0];
                String target = lineFields[1];
                String sourceNode = nodeDict.getString(Integer.parseInt(source));
                String targetNode = nodeDict.getString(Integer.parseInt(target));
                writer.write(sourceNode + "\t" + targetNode + "\n");
            }
            writer.close();
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void translateMatrixFile(String matrixFilename,
                                           String pathFilename,
                                           Dictionary nodeDict) {
        List<String> paths = new ArrayList<String>();
        try {
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(pathFilename));
            while ((line = reader.readLine()) != null) {
                paths.add(line.split("\t")[0]);
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String outFilename = matrixFilename + ".translated";
        try {
            FileWriter writer = new FileWriter(outFilename);
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(matrixFilename));
            while ((line = reader.readLine()) != null) {
                String[] lineFields = line.split("\t");
                String pair = lineFields[0];
                String pathPairs = lineFields[1];
                String[] pairFields = pair.split(",");
                int sourceId = Integer.parseInt(pairFields[0]);
                String sourceNode = nodeDict.getString(sourceId);
                int targetId = Integer.parseInt(pairFields[1]);
                String targetNode = nodeDict.getString(targetId);
                String[] pathPairFields = pathPairs.split(" -#- ");
                for (int i = 0; i < pathPairFields.length; i++) {
                    String[] fields = pathPairFields[i].split(",");
                    int pathId = Integer.parseInt(fields[0]);
                    String translated = "";
                    translated += sourceNode + "\t";
                    translated += targetNode + "\t";
                    translated += paths.get(pathId) + "\t";
                    translated += fields[1] + "\n";
                    writer.write(translated);
                }
                writer.write("\n");
            }
            writer.close();
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void translateWeightFile(String weightFilename, Dictionary edgeDict) {
        String outFilename = weightFilename + ".translated";
        List<Pair<Double, String>> weights = new ArrayList<Pair<Double, String>>();
        try {
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(weightFilename));
            while ((line = reader.readLine()) != null) {
                String[] lineFields = line.split("\t");
                String path = lineFields[0];
                double weight = Double.parseDouble(lineFields[1]);
                String translatedPath = translatePath(path, edgeDict);
                weights.add(new Pair<Double, String>(weight, translatedPath));
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Collections.sort(weights, new PairComparator<Double, String>(PairComparator.Side.NEGLEFT));
        try {
            FileWriter writer = new FileWriter(outFilename);
            for (Pair<Double, String> weightPath : weights) {
                writer.write(weightPath.getRight() + "\t" + weightPath.getLeft() + "\n");
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void translatePathFile(String pathFilename, Dictionary edgeDict) {
        String outFilename = pathFilename + ".translated";
        try {
            FileWriter writer = new FileWriter(outFilename);
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(pathFilename));
            while ((line = reader.readLine()) != null) {
                String[] lineFields = line.split("\t");
                String path = lineFields[0];
                String count = lineFields[1];
                String translatedPath = translatePath(path, edgeDict);
                writer.write(translatedPath + "\t" + count + "\n");
            }
            writer.close();
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void translateScoreFile(String scoreFilename, Dictionary nodeDict) {
        String outFilename = scoreFilename + ".translated";
        try {
            FileWriter writer = new FileWriter(outFilename);
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(scoreFilename));
            while ((line = reader.readLine()) != null) {
                if (line.equals("")) {
                    writer.write("\n");
                    continue;
                }
                String[] lineFields = line.split("\t");
                String source = lineFields[0];
                String sourceString = nodeDict.getString(Integer.parseInt(source));
                if (lineFields.length == 1) {
                    writer.write(sourceString + "\t\t\t\n");
                    continue;
                }
                String target = lineFields[1];
                String score = lineFields[2];
                String correct = "";
                if (lineFields.length == 4) {
                    correct = lineFields[3];
                }
                String targetString = nodeDict.getString(Integer.parseInt(target));
                writer.write(sourceString + "\t" + targetString + "\t" + score
                             + "\t" + correct + "\n");
            }
            writer.close();
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String translatePath(String path, Dictionary edgeDict) {
        // path is formatted like -1-2-3-4-; doing split("-") would result in an empty string as
        // the first element, so we call substring(1) first.
        String[] pathFields = path.substring(1).split("-");
        String translatedPath = "-";
        for (int i = 0; i < pathFields.length; i++) {
            boolean reverse = false;
            if (pathFields[i].charAt(0) == '_') {
                reverse = true;
                pathFields[i] = pathFields[i].substring(1);
            }
            String edgeType = pathFields[i];
            int edgeId = Integer.parseInt(edgeType);
            String edge = edgeDict.getString(edgeId);
            if (reverse) {
                edge = "_" + edge;
            }
            translatedPath += edge + "-";
        }
        return translatedPath;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Some utility methods that you probably shouldn't use, because it's really pretty internal
    // stuff.  They are kept public on the off-chance that they are useful, but they are not
    // documented.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static FastSharder createSharder(String graphName, int numShards) throws IOException {
        return new FastSharder<EmptyType, Integer>(graphName, numShards, null,
                new EdgeProcessor<Integer>() {
                    public Integer receiveEdge(int from, int to, String token) {
                        return Integer.parseInt(token);
                    }
                }, null, new IntConverter());
    }

    public static void outputPaths(String filename, Map<String, Integer> pathCounts)
            throws IOException {
        FileWriter writer = new FileWriter(filename);
        List<Map.Entry<String, Integer>> list =
            new ArrayList<Map.Entry<String, Integer>>(pathCounts.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return -o1.getValue().compareTo(o2.getValue());
            }
        });
        for (Map.Entry<String, Integer> entry : list) {
            writer.write(entry.getKey() + "\t" + entry.getValue() + "\n");
        }
        writer.close();
    }

    public static List<PathType> selectPaths(Map<String, Integer> pathCounts, int numPaths) {
        List<Map.Entry<String, Integer>> list =
            new ArrayList<Map.Entry<String, Integer>>(pathCounts.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return -o1.getValue().compareTo(o2.getValue());
            }
        });
        List<PathType> pathTypes = new ArrayList<PathType>();
        for (int i=0; i<numPaths; i++) {
            if (i >= list.size()) break;
            pathTypes.add(new PathType(list.get(i).getKey()));
        }
        return pathTypes;
    }

    public static Map<String, Integer> collapseInverses(Map<String, Integer> pathCounts,
            Map<String, String> inverses) {
        Map<String, Integer> newCounts = new HashMap<String, Integer>();
        for (String path : pathCounts.keySet()) {
            String translated = "-";
            String[] edges = path.split("-");
            for (String edge : edges) {
                if (edge.length() == 0) continue;
                boolean reverse = false;
                if (edge.charAt(0) == '_') {
                    reverse = true;
                    edge = edge.substring(1);
                }
                if (inverses.containsKey(edge)) {
                    edge = inverses.get(edge);
                    reverse = !reverse;
                }
                if (reverse) {
                    edge = "_" + edge;
                }
                translated += edge + "-";
            }
            int oldCount = pathCounts.get(path);
            Integer curCount = newCounts.get(translated);
            if (curCount == null) {
                newCounts.put(translated, oldCount);
            } else {
                newCounts.put(translated, oldCount + curCount);
            }
        }
        return newCounts;
    }

    public static void outputFeatureMatrix(String filename, List<MatrixRow> rows)
            throws IOException {
        FileWriter writer = new FileWriter(filename);
        for (MatrixRow row : rows) {
            writer.write(row.sourceNode + "," + row.targetNode + "\t");
            for (int i=0; i<row.columns; i++) {
                writer.write(row.pathTypes[i] + "," + row.values[i] + " -#- ");
            }
            writer.write("\n");
        }
        writer.close();
    }

    public static List<MatrixRow> readFeatureMatrixFromFile(String filename) throws IOException {
        return readFeatureMatrixFromFile(new BufferedReader(new FileReader(new File(filename))));
    }

    public static List<MatrixRow> readFeatureMatrixFromFile(BufferedReader reader)
            throws IOException {
        List<MatrixRow> featureMatrix = new ArrayList<MatrixRow>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineFields = line.split("\t");
            String[] nodeFields = lineFields[0].split(",");
            int sourceNode = Integer.parseInt(nodeFields[0]);
            int targetNode = Integer.parseInt(nodeFields[1]);
            String[] featureFields = lineFields[1].split(" -#- ");
            int[] pathTypes = new int[featureFields.length];
            double[] values = new double[featureFields.length];
            for (int i = 0; i < featureFields.length; i++) {
                String[] featureParts = featureFields[i].split(",");
                pathTypes[i] = Integer.parseInt(featureParts[0]);
                values[i] = Double.parseDouble(featureParts[1]);
            }
            featureMatrix.add(new MatrixRow(sourceNode, targetNode, pathTypes, values));
        }

        return featureMatrix;
    }

    public static Instance matrixRowToInstance(MatrixRow row, Alphabet alphabet, boolean positive) {
        double value = positive ? 1.0 : 0.0;
        FeatureVector feature_vector = new FeatureVector(alphabet, row.pathTypes, row.values);
        return new Instance(feature_vector, value, row.sourceNode + " " + row.targetNode, null);
    }

    public static void outputWeights(String filename, List<Double> weights,
            List<PathType> pathTypes) throws IOException {
        FileWriter writer = new FileWriter(filename);
        for (int i=0; i<pathTypes.size(); i++) {
            writer.write(pathTypes.get(i).description + "\t" + weights.get(i) + "\n");
        }
        writer.close();
    }

    public static List<Pair<Integer, Integer>> readSourceTargetPairs(String filename)
            throws IOException {
        return readSourceTargetPairs(new BufferedReader(new FileReader(new File(filename))));
    }

    public static List<Pair<Integer, Integer>> readSourceTargetPairs(BufferedReader reader)
            throws IOException {
        String line;
        List<Pair<Integer, Integer>> data = new ArrayList<Pair<Integer, Integer>>();
        while ((line = reader.readLine()) != null) {
            // File format is a list of (source, target) pairs, both as integers.
            String[] parts = line.split("\t");
            int source = Integer.parseInt(parts[0]);
            int target = Integer.parseInt(parts[1]);
            data.add(new Pair<Integer, Integer>(source, target));
        }
        return data;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Main method stuff, including command line processing.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////


    private static Logger logger = ChiLogger.getLogger("pra-driver");

    public static void main(String[] args)
            throws IOException, InterruptedException {
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
        cmdLineOptions.addOption(null, "useunseenasnegative", true,
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
        if (cmdLine.getOptionValue("useunseenasnegative", "false").equals("true")) {
            builder.useUnseenAsNegative();
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
            builder.setAllowedTargets(readIntegerSetFromFile(targets_file));
        }

        // Process the "to ignore" file (containing relations that you can't use between a source
        // node and its corresponding target)
        String toIgnoreFilename = cmdLine.getOptionValue("toignore");
        builder.setUnallowedEdges(readIntegerSetFromFile(toIgnoreFilename));

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
