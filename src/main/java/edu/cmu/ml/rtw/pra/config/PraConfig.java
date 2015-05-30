package edu.cmu.ml.rtw.pra.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.pra.experiments.Outputter;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.Vector;

/**
 * Holds things that are needed in more than one place in the PRA code.  The basic design of the
 * PRA configuration is that the params object holds things that are specific to each component of
 * the code: graph creation looks under the "graphs" section of the params object, and so on.  But
 * feature generation needs access to the graph, for instance, and we don't want to repeat
 * construction or processing of the params object.  So if a component needs to process some
 * arguments from the params object and then make something persistent, it places it in this config
 * object.
 *
 * This isn't how this object originally worked; it used to hold all of the parameters used
 * everywhere, and was initialized at the beginning with everything it needed.  I'm slowly moving
 * away from that, taking parameters that are only needed in one place and keeping them only in the
 * params object.  I'm pretty close to done with this, but there are a few parameters that are
 * left.
 *
 * TODO(matt): this has entries both for global-level information and for relation-specific
 * configuration (e.g., the graph is global, over all relations being run, while the allowed
 * targets are per-relation).  These two functions really should be split.  The issue that I really
 * only want to create a FeatureGenerator once for each run of Driver, but FeatureGenerator takes
 * the config as a class parameter, and that's how it gets relation-specific information.  That
 * means that whatever FeatureGenerator has to, it has to do it for each relation, even if some of
 * it is expensive and can be kept between relations (like loading the graph into memory, for
 * instance).
 */
public class PraConfig {
  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Graph-related objects
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // Path to the graph file to use for the random walks.
  public final String graph;

  // The number of shards that the graph is (or should be) sharded into.
  public final int numShards;

  // TODO(matt): the dictionaries are stored in the Graph object now, and that's really where they
  // should stay...  Remove them from here.
  // A Dictionary mapping node names to integers (neither this dictionary nor the one below it are
  // necessary for running the PRA code.  They are just convenient for some use cases, particularly
  // if you want human readable output).
  public final Dictionary nodeDict;

  // A Dictionary mapping edge names to integers.
  public final Dictionary edgeDict;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Output-related objects
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // If not null, a directory where we should save the output for later inspection.  If null, we do
  // not save anything.
  public final String outputBase;

  // A class that handles output.  If you want a node name translator (which is in addition to the
  // node dictionary, like translating Freebase MIDs to human-readable strings), you need to
  // initialize this yourself and set it in the builder.  Otherwise, we'll just use the nodeDict
  // and edgeDict that are already here.
  public final Outputter outputter;

  // Whether or not we should save the train/test matrices that are created when running PRA.
  // These files can be very large, so if all you care about is the MAP or MRR score of each run,
  // you should probably not create them.  They can be very helpful for debugging, though.  I would
  // recommend leaving this as false (the default), unless you need to debug something or do some
  // error analysis, then you can set it to true.
  public final boolean outputMatrices;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Split-related objects - what do we use to train and test?
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

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Relation-metadata-related objects - inverses, ranges, and the like
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // The relation that PRA is learning a model for.
  public final String relation;

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

  // If not null, and in combination with the acceptPolicy, this specifies which nodes are allowed
  // to be targets in the feature matrix.
  public final Set<Integer> allowedTargets;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // The Builder code.  Unless you're adding a parameter, you shouldn't need to worry about
  // anything below this.  It's unfortunate that you have to add the same parameter in five places,
  // but I'm not sure I know a way around that...
  ////////////////////////////////////////////////////////////////////////////////////////////////

  private PraConfig(Builder builder) {
    relation = builder.relation;
    graph = builder.graph;
    numShards = builder.numShards;
    allData = builder.allData;
    percentTraining = builder.percentTraining;
    trainingData = builder.trainingData;
    testingData = builder.testingData;
    allowedTargets = builder.allowedTargets;
    unallowedEdges = builder.unallowedEdges;
    relationInverses = builder.relationInverses;
    outputBase = builder.outputBase;
    nodeDict = builder.nodeDict;
    edgeDict = builder.edgeDict;
    outputter = builder.outputter;
    outputMatrices = builder.outputMatrices;
  }

  public static class Builder {
    private String relation;
    private String graph;
    private int numShards = -1;
    private Dataset allData;
    private double percentTraining = -1.0;
    private Dataset trainingData;
    private Dataset testingData;
    private Set<Integer> allowedTargets;
    private List<Integer> unallowedEdges;
    private Map<Integer, Integer> relationInverses;
    private String outputBase;
    private boolean outputMatrices;
    // These three are public because that makes things easier in KbPraDriver.
    public Dictionary nodeDict = new Dictionary();
    public Dictionary edgeDict = new Dictionary();
    public Outputter outputter = null;

    private boolean noChecks = false;

    public Builder() {}
    public Builder setRelation(String relation) {this.relation = relation;return this;}
    public Builder setGraph(String graph) {this.graph = graph;return this;}
    public Builder setNumShards(int numShards) {this.numShards = numShards;return this;}
    public Builder setAllData(Dataset d) {this.allData = d;return this;}
    public Builder setPercentTraining(double p) {this.percentTraining = p;return this;}
    public Builder setTrainingData(Dataset t) {this.trainingData = t;return this;}
    public Builder setTestingData(Dataset t) {this.testingData = t;return this;}
    public Builder setAllowedTargets(Set<Integer> a) {this.allowedTargets = a;return this;}
    public Builder setUnallowedEdges(List<Integer> e) {this.unallowedEdges = e;return this;}
    public Builder setRelationInverses(Map<Integer, Integer> i) {relationInverses = i;return this;}
    public Builder setOutputBase(String outputBase) {this.outputBase = outputBase;return this;}
    public Builder setNodeDictionary(Dictionary d) {this.nodeDict = d;return this;}
    public Builder setEdgeDictionary(Dictionary d) {this.edgeDict = d;return this;}
    public Builder setOutputter(Outputter o) {this.outputter = o;return this;}
    public Builder setOutputMatrices(boolean o) {this.outputMatrices = o;return this;}

    public PraConfig build() {
      if (outputter == null) {
        outputter = new Outputter(nodeDict, edgeDict, null, new FileUtil());
      }
      if (noChecks) return new PraConfig(this);

      // Check that we have a consistent state, with everything specified that is necessary for
      // running PRA.
      if (relation == null) throw new IllegalStateException("relation must be set");
      if (graph == null) throw new IllegalStateException("graph must be set");
      if (numShards == -1) throw new IllegalStateException("numShards must be set");
      if (allData != null && percentTraining == -1.0) throw new IllegalStateException(
          "Must give percent training when specifying allData");
      if (allData != null && trainingData != null) throw new IllegalStateException(
          "allData and trainingData are mutually exclusive");
      if (allData == null && trainingData == null) throw new IllegalStateException(
          "Must specify either allData or trainingData");
      return new PraConfig(this);
    }

    // This is for methods like PraDriver.crossValidate, which take a PraConfig, modify a few
    // things, and pass it to another method (PraDriver.trainAndTest, in this case).
    public Builder(PraConfig config) {
      setRelation(config.relation);
      setGraph(config.graph);
      setNumShards(config.numShards);
      setAllData(config.allData);
      setPercentTraining(config.percentTraining);
      setTrainingData(config.trainingData);
      setTestingData(config.testingData);
      setAllowedTargets(config.allowedTargets);
      setUnallowedEdges(config.unallowedEdges);
      setRelationInverses(config.relationInverses);
      setOutputBase(config.outputBase);
      setNodeDictionary(config.nodeDict);
      setEdgeDictionary(config.edgeDict);
      setOutputter(config.outputter);
      setOutputMatrices(config.outputMatrices);
    }

    /**
     * Disables consistency checks. This can be used if you're building an intermediate Builder
     * object (as in KbPraDriver.java or Driver.scala), or if you're just writing a simple test of
     * something that needs part of a PraConfig, but not all of it.
     */
    public Builder noChecks() {
      this.noChecks = true;
      return this;
    }
  }
}
