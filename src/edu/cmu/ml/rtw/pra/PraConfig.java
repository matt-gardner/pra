package edu.cmu.ml.rtw.pra;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.ml.rtw.util.Pair;

public class PraConfig {
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // GraphChi parameters
    // -------------------
    // This is just the graph location and number of shards, really.  And, just for fun, I'll stick
    // other random parameters in here, like an output directory.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // Path to the graph file to use for the random walks.
    public final String graph;

    // The number of shards that the graph is (or should be) sharded into.
    public final int numShards;

    // If not null, a directory where we should save the output for later inspection.  If null, we
    // do not save anything.
    public final String outputBase;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Data specification parameters
    // -----------------------------
    // Parameters that tell the PRA code where the data is and which of it is training and testing,
    // along with a few other important pieces of information about the relation being learned and
    // the edge types in the graph.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // A list of (source, target) pairs, to be split into training and testing data.  This is
    // intended to be mutually exclusive with trainingData and testingData.  Either you specify
    // allData and percentTraining, whereby we will take care of splitting it into training and
    // testing, or you specify trainingData and testingData yourself and leave allData blank.
    public final Dataset allData;

    // How much of allData (after shuffling) should be used for training.
    public final double percentTraining;

    // A list of (source, target) training instances.
    public final Dataset trainingData;

    // A list of (source, target) testing instances.
    public final Dataset testingData;

    // A map from relation (strings) to their inverses, for use in collapsing equivalent path types
    // that we find.  Note that any particular relation should either be a key or a value in this
    // map, not both - we check relation membership in the key set, and if it's present, replace it
    // with the corresponding value.  If every relation is in the key set with its inverse, you
    // won't collapse anything, you'll just swap all of the relations.
    // TODO(matt): I'm not sure that this is used correctly when computing the feature matrix...
    // Are there guaranteed to be two edges in the graph we construct when there are inverses?  If
    // so, we're ok.  If not, we may be inadvertently skipping edges when computing feature values.
    public final Map<String, String> relationInverses;

    // Let's wait on these two, until I settle on the right way to provide training data to the PRA
    // code.  These might end up in a separate NELL-PRA piece of code.
    //public final Set<Integer> domainNodes;
    //public final Set<Integer> rangeNodes;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Path Finding parameters
    // -----------------------
    // Anything that affects the first step of computation, which finding a set of candidate path
    // types (or features) and selecting the top k of them.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // The number of iterations to go when finding paths (more iterations implies longer potential
    // paths).
    public final int numIters;

    // How many random walks to start from each node, during the path finding step.
    public final int walksPerSource;

    // A set of edge types that are not allowed to be followed between a source and its
    // corresponding target.  This allows you to exclude the edge you are trying to learn a
    // classifier for, for instance, so you aren't cheating.
    public final Set<Integer> unallowedEdges;

    // The number of paths to keep (or, the number of columns to compute in the feature matrix).
    public final int numPaths;

    // Determines whether to keep paths that we find between _all_ sources and _all_ targets in the
    // training data, or just between known (source, target) _pairs_.  See PathTypePolicy for more
    // information.
    public final PathTypePolicy pathTypePolicy;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Feature computation parameters
    // ------------------------------
    // Anything that affects the second (and fourth) step of computation, which is computing
    // feature values for all of the selected features, by following path types in the graph.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // The number of walks to start for each (source node, path) combination, in the feature
    // computation step.
    public final int walksPerPath;

    // Determines the set of acceptable rows in the feature matrix.  For example, any (source,
    // target) pair could be acceptable, or any (source, target) pair where target is a known
    // positive instance for some source.  See MatrixRowPolicy for a more complete description of
    // allowable values for this parameter.
    public final MatrixRowPolicy acceptPolicy;

    // If not null, and in combination with the acceptPolicy, this specifies which nodes are
    // allowed to be targets in the feature matrix.
    public final Set<Integer> allowedTargets;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Classifier learning parameters
    // ------------------------------
    // Anything that affects the third step of computation, which is training a classifier given
    // a feature matrix.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // Whether to use examples as negative instances that were not explicitly given as negative.
    // That is, if the random walks find a connection between a source node and a target node that
    // was not given as either a positive or negative example, should we use it as a negative
    // example?
    public final boolean useUnseenAsNegative;

    // Weight to use for L2 regularization.
    public final double l2Weight;

    // Weight to use for L1 regularization (if both L2 and L1 are set, we will use both).
    public final double l1Weight;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The Builder code.  Unless you're adding a parameter, you shouldn't need to worry about
    // anything below this.  It's unfortunate that you have to add the same parameter in five
    // places, but I'm not sure I know a way around that...
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private PraConfig(Builder builder) {
        graph = builder.graph;
        numShards = builder.numShards;
        numIters = builder.numIters;
        walksPerSource = builder.walksPerSource;
        numPaths = builder.numPaths;
        pathTypePolicy = builder.pathTypePolicy;
        walksPerPath = builder.walksPerPath;
        acceptPolicy = builder.acceptPolicy;
        l2Weight = builder.l2Weight;
        l1Weight = builder.l1Weight;
        useUnseenAsNegative = builder.useUnseenAsNegative;
        allData = builder.allData;
        percentTraining = builder.percentTraining;
        trainingData = builder.trainingData;
        testingData = builder.testingData;
        //domainNodes = builder.domainNodes;
        //rangeNodes = builder.rangeNodes;
        allowedTargets = builder.allowedTargets;
        unallowedEdges = builder.unallowedEdges;
        relationInverses = builder.relationInverses;
        outputBase = builder.outputBase;
    }

    public static class Builder {
        private String graph;
        private int numShards;
        private int numIters;
        private int walksPerSource;
        private int numPaths;
        private PathTypePolicy pathTypePolicy;
        private int walksPerPath;
        private MatrixRowPolicy acceptPolicy;
        private double l2Weight;
        private double l1Weight;
        private boolean useUnseenAsNegative;
        private Dataset allData;
        private double percentTraining;
        private Dataset trainingData;
        private Dataset testingData;
        //private Set<Integer> domainNodes;
        //private Set<Integer> rangeNodes;
        private Set<Integer> allowedTargets;
        private Set<Integer> unallowedEdges;
        private Map<String, String> relationInverses;
        private String outputBase;

        public Builder() { }
        public Builder setGraph(String graph) {this.graph = graph;return this;}
        public Builder setNumShards(int numShards) {this.numShards = numShards;return this;}
        public Builder setNumIters(int numIters) {this.numIters = numIters;return this;}
        public Builder setWalksPerSource(int w) {this.walksPerSource = w;return this;}
        public Builder setNumPaths(int numPaths) {this.numPaths = numPaths;return this;}
        public Builder setPathTypePolicy(PathTypePolicy p) {this.pathTypePolicy = p;return this;}
        public Builder setWalksPerPath(int w) {this.walksPerPath = w;return this;}
        public Builder setAcceptPolicy(MatrixRowPolicy p) {this.acceptPolicy = p;return this;}
        public Builder setL2Weight(double l2Weight) {this.l2Weight = l2Weight;return this;}
        public Builder setL1Weight(double l1Weight) {this.l1Weight = l1Weight;return this;}
        public Builder useUnseenAsNegative() {this.useUnseenAsNegative = true;return this;}
        public Builder setAllData(Dataset d) {this.allData = d;return this;}
        public Builder setPercentTraining(double p) {this.percentTraining = p;return this;}
        public Builder setTrainingData(Dataset t) {this.trainingData = t;return this;}
        public Builder setTestingData(Dataset t) {this.testingData = t;return this;}
        //public Builder setDomainNodes(Set<Integer> n) {this.domainNodes = n;return this;}
        //public Builder setRangeNodes(Set<Integer> n) {this.rangeNodes = n;return this;}
        public Builder setAllowedTargets(Set<Integer> a) {this.allowedTargets = a;return this;}
        public Builder setUnallowedEdges(Set<Integer> e) {this.unallowedEdges = e;return this;}
        public Builder setRelationInverses(Map<String, String> i) {this.relationInverses = i;return this;}
        public Builder setOutputBase(String outputBase) {this.outputBase = outputBase;return this;}

        public PraConfig build() {
            // TODO(matt): verify that all necessary options have been set.
            return new PraConfig(this);
        }

        // This is for methods like PraDriver.crossValidate, which take a PraConfig, modify a few
        // things, and pass it to another method (PraDriver.trainAndTest, in this case).
        public Builder(PraConfig config) {
            setGraph(config.graph);
            setNumShards(config.numShards);
            setNumIters(config.numIters);
            setWalksPerSource(config.walksPerSource);
            setNumPaths(config.numPaths);
            setPathTypePolicy(config.pathTypePolicy);
            setWalksPerPath(config.walksPerPath);
            setAcceptPolicy(config.acceptPolicy);
            setL2Weight(config.l2Weight);
            setL1Weight(config.l1Weight);
            if (config.useUnseenAsNegative) useUnseenAsNegative();
            setAllData(config.allData);
            setPercentTraining(config.percentTraining);
            setTrainingData(config.trainingData);
            setTestingData(config.testingData);
            //setDomainNodes(config.domainNodes);
            //setRangeNodes(config.rangeNodes);
            setAllowedTargets(config.allowedTargets);
            setUnallowedEdges(config.unallowedEdges);
            setRelationInverses(config.relationInverses);
            setOutputBase(config.outputBase);
        }
    }
}
