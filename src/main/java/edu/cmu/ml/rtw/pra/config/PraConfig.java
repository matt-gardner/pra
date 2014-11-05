package edu.cmu.ml.rtw.pra.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.pra.experiments.Outputter;
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory;
import edu.cmu.ml.rtw.pra.features.EdgeExcluderFactory;
import edu.cmu.ml.rtw.pra.features.MatrixPathFollowerFactory;
import edu.cmu.ml.rtw.pra.features.MatrixRowPolicy;
import edu.cmu.ml.rtw.pra.features.MostFrequentPathTypeSelector;
import edu.cmu.ml.rtw.pra.features.PathFollowerFactory;
import edu.cmu.ml.rtw.pra.features.PathTypeFactory;
import edu.cmu.ml.rtw.pra.features.PathTypePolicy;
import edu.cmu.ml.rtw.pra.features.PathTypeSelector;
import edu.cmu.ml.rtw.pra.features.RandomWalkPathFollowerFactory;
import edu.cmu.ml.rtw.pra.features.SingleEdgeExcluderFactory;
import edu.cmu.ml.rtw.pra.features.VectorClusteringPathTypeSelector;
import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.Vector;

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

  // If not null, a directory where we should save the output for later inspection.  If null, we do
  // not save anything.
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

  // A map from relation (indices) to their inverses.  This is used in two places.  First, we use
  // this to restrict random walks along edges that would be cheating.  If we're trying to learn a
  // model for a particular relation, we cannot use the presence of its inverse in the graph.
  // Second, we (optionally, depending on the PathTypeFactory) use this to collapse PathTypes that
  // are redundant with each other.  The second bit is a little tricky, though, because it depends
  // on the graph being constructed in just the right way, so by default we just do a no-op there.
  //
  // This map should contain every relation with a known inverse as both a key and a value
  // (otherwise edge exclusion will not work correctly), and it should use the edgeDict to map
  // those relations to integers.
  public final Map<Integer, Integer> relationInverses;

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

  // A set of edge types that should be excluded during walks.  The semantics of this are
  // particular and a little complicated.  How we exclude edges as follows: in a walk that
  // _started_ from the source node, you cannot follow an edge from source to target with the given
  // edge type.  The intended result is that for any particular walk, we effectively remove a
  // single edge from the graph - the edge that connects the source node to the target node with
  // that edge type.  We don't want to exlucde _all_ edges of any particular type, because other
  // known instances of a relation are valuable evidence when making predictions.
  //
  // Note that just giving a set of edge types to exclude doesn't work, even though we also have a
  // list of data points.  You might think that you can just give them both separately, and then
  // any time you see one of the edge types between an input (source, target) pair, you can exclude
  // it.  That's how the code used to work, but it breaks if the edges type you're trying to
  // exclude are a latent representation of the original edge - there may be other edges with the
  // same representation that you don't want to exclude.  So we take a _list_, and exclude _one
  // instance_ of each edge type for each entry in the list.  Before doing any particular walk, the
  // driver will combine this list with the (training or testing) data points that will be used,
  // and input a list of edges to the GraphChi code.
  //
  // Also note that even though this is in the "path finding parameters" section, it's also used
  // during feature computation.
  public final List<Integer> unallowedEdges;
  public final EdgeExcluderFactory edgeExcluderFactory;

  // The number of paths to keep (or, the number of columns to compute in the feature matrix).
  public final int numPaths;

  // Determines whether to keep paths that we find between _all_ sources and _all_ targets in the
  // training data, or just between known (source, target) _pairs_.  See PathTypePolicy for more
  // information.
  public final PathTypePolicy pathTypePolicy;

  // Determines what kind of PathTypes will be used when extracting features.  Defaults to
  // BasicPathTypeFactory.  See PathTypeFactory for details.
  public final PathTypeFactory pathTypeFactory;

  // Determines which path types to keep after doing a random walk to find potential candidates.
  public final PathTypeSelector pathTypeSelector;


  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Feature computation parameters
  // ------------------------------
  // Anything that affects the second (and fourth) step of computation, which is computing feature
  // values for all of the selected features, by following path types in the graph.
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // There are currently two different PathFollowers: one that does random walks to compute path
  // probabilities, and one that does matrix multiplications.  This parameter lets you set which
  // one you want to use.
  public final PathFollowerFactory pathFollowerFactory;

  // The number of walks to start for each (source node, path) combination, in the feature
  // computation step.
  public final int walksPerPath;

  // Determines the set of acceptable rows in the feature matrix.  For example, any (source,
  // target) pair could be acceptable, or any (source, target) pair where target is a known
  // positive instance for some source.  See MatrixRowPolicy for a more complete description of
  // allowable values for this parameter.
  public final MatrixRowPolicy acceptPolicy;

  // If not null, and in combination with the acceptPolicy, this specifies which nodes are allowed
  // to be targets in the feature matrix.
  public final Set<Integer> allowedTargets;

  // I typically have normalized the random walk probabilities over targets for each (source, path
  // type) pair.  When I was at Google I discovered that Ni doesn't do this in his code.  This
  // parameter lets you control whether or not you should normalize probabilities.  Currently
  // defaults to true.
  public final boolean normalizeWalkProbabilities;


  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Classifier learning parameters
  // ------------------------------
  // Anything that affects the third step of computation, which is training a classifier given a
  // feature matrix.
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // Whether to use examples as negative instances that were not explicitly given as negative.
  // That is, if the random walks find a connection between a source node and a target node that
  // was not given as either a positive or negative example, should we use it as a negative
  // example?  If this parameter is true, we will not use it.
  public final boolean onlyExplicitNegatives;

  // Weight to use for L2 regularization.
  public final double l2Weight;

  // Weight to use for L1 regularization (if both L2 and L1 are set, we will use both).
  public final double l1Weight;

  // We generally use probabilities as feature values when learning models and performing
  // classification.  Setting this to true allows you to use simple 1-0 features instead, where any
  // positive value is set to 1.  I have been told that Ni experimented with this and found that
  // binarizing the features performed worse than using probabilities.  I am adding this parameter
  // so that I can test this myself.
  public final boolean binarizeFeatures;


  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Some other random stuff
  // -----------------------
  // Including dictionaries mapping node and edge strings to integers, and an object that handles
  // all of the output.
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // A Dictionary mapping node names to integers (neither this dictionary nor the one below it are
  // necessary for running the PRA code.  They are just convenient for some use cases, particularly
  // if you want human readable output).
  public final Dictionary nodeDict;

  // A Dictionary mapping edge names to integers.
  public final Dictionary edgeDict;

  // A class that handles output.  If you want a node name translator (which is in addition to the
  // node dictionary, like translating Freebase MIDs to human-readable strings), you need to
  // initialize this yourself and set it in the builder.  Otherwise, we'll just use the nodeDict
  // and edgeDict that are already here.
  public final Outputter outputter;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // The Builder code.  Unless you're adding a parameter, you shouldn't need to worry about
  // anything below this.  It's unfortunate that you have to add the same parameter in five places,
  // but I'm not sure I know a way around that...
  ////////////////////////////////////////////////////////////////////////////////////////////////

  private PraConfig(Builder builder) {
    graph = builder.graph;
    numShards = builder.numShards;
    numIters = builder.numIters;
    walksPerSource = builder.walksPerSource;
    numPaths = builder.numPaths;
    pathTypePolicy = builder.pathTypePolicy;
    pathTypeFactory = builder.pathTypeFactory;
    pathTypeSelector = builder.pathTypeSelector;
    pathFollowerFactory = builder.pathFollowerFactory;
    walksPerPath = builder.walksPerPath;
    acceptPolicy = builder.acceptPolicy;
    l2Weight = builder.l2Weight;
    l1Weight = builder.l1Weight;
    binarizeFeatures = builder.binarizeFeatures;
    onlyExplicitNegatives = builder.onlyExplicitNegatives;
    allData = builder.allData;
    percentTraining = builder.percentTraining;
    trainingData = builder.trainingData;
    testingData = builder.testingData;
    //domainNodes = builder.domainNodes;
    //rangeNodes = builder.rangeNodes;
    allowedTargets = builder.allowedTargets;
    normalizeWalkProbabilities = builder.normalizeWalkProbabilities;
    unallowedEdges = builder.unallowedEdges;
    edgeExcluderFactory = builder.edgeExcluderFactory;
    relationInverses = builder.relationInverses;
    outputBase = builder.outputBase;
    nodeDict = builder.nodeDict;
    edgeDict = builder.edgeDict;
    outputter = builder.outputter;
  }

  public static class Builder {
    private String graph;
    private int numShards;
    private int numIters;
    private int walksPerSource;
    private int numPaths;
    private PathTypePolicy pathTypePolicy = PathTypePolicy.PAIRED_ONLY;
    public PathTypeFactory pathTypeFactory = new BasicPathTypeFactory();
    private PathTypeSelector pathTypeSelector = new MostFrequentPathTypeSelector();
    private PathFollowerFactory pathFollowerFactory = new RandomWalkPathFollowerFactory();
    private int walksPerPath;
    private MatrixRowPolicy acceptPolicy = MatrixRowPolicy.ALL_TARGETS;
    private double l2Weight;
    private double l1Weight;
    private boolean binarizeFeatures = false;
    private boolean onlyExplicitNegatives;
    private Dataset allData;
    private double percentTraining;
    private Dataset trainingData;
    private Dataset testingData;
    //private Set<Integer> domainNodes;
    //private Set<Integer> rangeNodes;
    private Set<Integer> allowedTargets;
    private boolean normalizeWalkProbabilities = true;
    private List<Integer> unallowedEdges;
    private EdgeExcluderFactory edgeExcluderFactory = new SingleEdgeExcluderFactory();
    private Map<Integer, Integer> relationInverses;
    private String outputBase;
    // These two are public because that makes things easier in KbPraDriver.
    public Dictionary nodeDict = new Dictionary();
    public Dictionary edgeDict = new Dictionary();
    public Outputter outputter = null;

    public Builder() { }
    public Builder setGraph(String graph) {this.graph = graph;return this;}
    public Builder setNumShards(int numShards) {this.numShards = numShards;return this;}
    public Builder setNumIters(int numIters) {this.numIters = numIters;return this;}
    public Builder setPathFollowerFactory(PathFollowerFactory f) {this.pathFollowerFactory = f;return this;}
    public Builder setWalksPerSource(int w) {this.walksPerSource = w;return this;}
    public Builder setNumPaths(int numPaths) {this.numPaths = numPaths;return this;}
    public Builder setPathTypePolicy(PathTypePolicy p) {this.pathTypePolicy = p;return this;}
    public Builder setPathTypeFactory(PathTypeFactory p) {this.pathTypeFactory = p;return this;}
    public Builder setPathTypeSelector(PathTypeSelector p) {this.pathTypeSelector = p;return this;}
    public Builder setWalksPerPath(int w) {this.walksPerPath = w;return this;}
    public Builder setAcceptPolicy(MatrixRowPolicy p) {this.acceptPolicy = p;return this;}
    public Builder setL2Weight(double l2Weight) {this.l2Weight = l2Weight;return this;}
    public Builder setL1Weight(double l1Weight) {this.l1Weight = l1Weight;return this;}
    public Builder setBinarizeFeatures(boolean b) {this.binarizeFeatures = b;return this;}
    public Builder onlyExplicitNegatives() {this.onlyExplicitNegatives = true;return this;}
    public Builder setAllData(Dataset d) {this.allData = d;return this;}
    public Builder setPercentTraining(double p) {this.percentTraining = p;return this;}
    public Builder setTrainingData(Dataset t) {this.trainingData = t;return this;}
    public Builder setTestingData(Dataset t) {this.testingData = t;return this;}
    //public Builder setDomainNodes(Set<Integer> n) {this.domainNodes = n;return this;}
    //public Builder setRangeNodes(Set<Integer> n) {this.rangeNodes = n;return this;}
    public Builder setAllowedTargets(Set<Integer> a) {this.allowedTargets = a;return this;}
    public Builder setNormalizeWalkProbabilities(boolean b) {this.normalizeWalkProbabilities = b;return this;}
    public Builder setUnallowedEdges(List<Integer> e) {this.unallowedEdges = e;return this;}
    public Builder setEdgeExcluderFactory(EdgeExcluderFactory f) {edgeExcluderFactory = f; return this;}
    public Builder setRelationInverses(Map<Integer, Integer> i) {relationInverses = i;return this;}
    public Builder setOutputBase(String outputBase) {this.outputBase = outputBase;return this;}
    public Builder setNodeDictionary(Dictionary d) {this.nodeDict = d;return this;}
    public Builder setEdgeDictionary(Dictionary d) {this.edgeDict = d;return this;}
    public Builder setOutputter(Outputter o) {this.outputter = o;return this;}

    public PraConfig build() {
      if (outputter == null) {
        outputter = new Outputter(nodeDict, edgeDict);
      }
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
      setPathTypeFactory(config.pathTypeFactory);
      setPathTypeSelector(config.pathTypeSelector);
      setPathFollowerFactory(config.pathFollowerFactory);
      setWalksPerPath(config.walksPerPath);
      setAcceptPolicy(config.acceptPolicy);
      setL2Weight(config.l2Weight);
      setL1Weight(config.l1Weight);
      setBinarizeFeatures(config.binarizeFeatures);
      if (config.onlyExplicitNegatives) onlyExplicitNegatives();
      setAllData(config.allData);
      setPercentTraining(config.percentTraining);
      setTrainingData(config.trainingData);
      setTestingData(config.testingData);
      //setDomainNodes(config.domainNodes);
      //setRangeNodes(config.rangeNodes);
      setAllowedTargets(config.allowedTargets);
      setNormalizeWalkProbabilities(config.normalizeWalkProbabilities);
      setUnallowedEdges(config.unallowedEdges);
      setEdgeExcluderFactory(config.edgeExcluderFactory);
      setRelationInverses(config.relationInverses);
      setOutputBase(config.outputBase);
      setNodeDictionary(config.nodeDict);
      setEdgeDictionary(config.edgeDict);
      setOutputter(config.outputter);
    }

    public void setFromParamFile(BufferedReader reader) throws IOException {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split("\t");
        String parameter = fields[0];
        String value = fields[1];
        if (parameter.equalsIgnoreCase("L1 weight")) {
          setL1Weight(Double.parseDouble(value));
        } else if (parameter.equalsIgnoreCase("L2 weight")) {
          setL2Weight(Double.parseDouble(value));
        } else if (parameter.equalsIgnoreCase("walks per source")) {
          setWalksPerSource(Integer.parseInt(value));
        } else if (parameter.equalsIgnoreCase("walks per path")) {
          setWalksPerPath(Integer.parseInt(value));
        } else if (parameter.equalsIgnoreCase("path finding iterations")) {
          setNumIters(Integer.parseInt(value));
        } else if (parameter.equalsIgnoreCase("number of paths to keep")) {
          setNumPaths(Integer.parseInt(value));
        } else if (parameter.equalsIgnoreCase("only explicit negative evidence")) {
          onlyExplicitNegatives();
        } else if (parameter.equalsIgnoreCase("binarize features")) {
          setBinarizeFeatures(Boolean.parseBoolean(value));
        } else if (parameter.equalsIgnoreCase("normalize walk probabilities")) {
          setNormalizeWalkProbabilities(Boolean.parseBoolean(value));
        } else if (parameter.equalsIgnoreCase("matrix accept policy")) {
          setAcceptPolicy(MatrixRowPolicy.parseFromString(value));
        } else if (parameter.equalsIgnoreCase("path accept policy")) {
          setPathTypePolicy(PathTypePolicy.parseFromString(value));
        } else if (parameter.equalsIgnoreCase("path type embeddings")) {
          initializeVectorPathTypeFactory(value);
        } else if (parameter.equalsIgnoreCase("path type selector")) {
          initializePathTypeSelector(value);
        } else if (parameter.equalsIgnoreCase("path follower")) {
          initializePathFollowerFactory(value);
        } else {
          throw new RuntimeException("Unrecognized parameter specification: " + line);
        }
      }
    }

    public void initializeVectorPathTypeFactory(String paramString) throws IOException {
      System.out.println("Initializing vector path type factory");
      String[] params = paramString.split(",");
      double spikiness = Double.parseDouble(params[0]);
      double resetWeight = Double.parseDouble(params[1]);
      Map<Integer, Vector> embeddings = Maps.newHashMap();
      for (int embeddingsIndex = 2; embeddingsIndex < params.length; embeddingsIndex++) {
        String embeddingsFile = params[embeddingsIndex];
        System.out.println("Embeddings file: " + embeddingsFile);
        BufferedReader reader = new BufferedReader(new FileReader(embeddingsFile));
        String line;
        // Embeddings files are formated as tsv, where the first column is the relation name
        // and the rest of the columns make up the vector.
        while ((line = reader.readLine()) != null) {
          String[] fields = line.split("\t");
          int relationIndex = edgeDict.getIndex(fields[0]);
          double[] vector = new double[fields.length - 1];
          for (int i = 0; i < vector.length; i++) {
            vector[i] = Double.parseDouble(fields[i + 1]);
          }
          embeddings.put(relationIndex, new Vector(vector));
        }
      }
      setPathTypeFactory(new VectorPathTypeFactory(edgeDict, embeddings, spikiness, resetWeight));
    }

    public void initializePathTypeSelector(String paramString) {
      if (paramString.startsWith("VectorClusteringPathTypeSelector")) {
        System.out.println("Using VectorClusteringPathTypeSelector");
        String[] params = paramString.split(",");
        double similarityThreshold = Double.parseDouble(params[1]);
        PathTypeSelector selector = new VectorClusteringPathTypeSelector(
            (VectorPathTypeFactory) pathTypeFactory,
            similarityThreshold);
        setPathTypeSelector(selector);
      } else {
        throw new RuntimeException("Unrecognized path type selector parameter!");
      }
    }

    public void initializePathFollowerFactory(String paramString) {
      // TODO(matt): this really should validate that none of the other parameters are in conflict
      // with this one.  For instance, matrix multiplication doesn't currently work with vector
      // space random walks.
      if (paramString.equalsIgnoreCase("random walks")) {
        setPathFollowerFactory(new RandomWalkPathFollowerFactory());
      } else if (paramString.equalsIgnoreCase("matrix multiplication")) {
        setPathFollowerFactory(new MatrixPathFollowerFactory());
      }
    }

  }
}
