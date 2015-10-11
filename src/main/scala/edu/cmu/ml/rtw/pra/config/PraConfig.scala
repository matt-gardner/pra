package edu.cmu.ml.rtw.pra.config

import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.Graph
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FileUtil

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
 * TODO(matt): I want to remove this object entirely and just go with the
 * one-object-per-spec-parameter paradigm I've been using more recently.  That would mean having a
 * Graph object that I pass around, a Split object, an Outputter, and a RelationMetadata object.
 * The Driver creates all of these objects, and passes them to the other parts of the code that
 * need them.
 */
class PraConfig(builder: PraConfigBuilder) {
  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Graph-related objects
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // Path to the graph file to use for the random walks.
  val graph = builder.graph

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Output-related objects
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // If not null, a directory where we should save the output for later inspection.  If null, we do
  // not save anything.
  val outputBase = builder.outputBase

  // A class that handles output.  If you want a node name translator (which is in addition to the
  // node dictionary, like translating Freebase MIDs to human-readable strings), you need to
  // initialize this yourself and set it in the builder.  Otherwise, we'll just use the nodeDict
  // and edgeDict that are already here.
  val outputter = builder.outputter

  // Whether or not we should save the train/test matrices that are created when running PRA.
  // These files can be very large, so if all you care about is the MAP or MRR score of each run,
  // you should probably not create them.  They can be very helpful for debugging, though.  I would
  // recommend leaving this as false (the default), unless you need to debug something or do some
  // error analysis, then you can set it to true.
  val outputMatrices = builder.outputMatrices

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Split-related objects - what do we use to train and test?
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // A list of (source, target) pairs, to be split into training and testing data.  This is
  // intended to be mutually exclusive with trainingData and testingData.  Either you specify
  // allData and percentTraining, whereby we will take care of splitting it into training and
  // testing, or you specify trainingData and testingData yourself and leave allData blank.
  val allData = builder.allData

  // How much of allData (after shuffling) should be used for training.
  val percentTraining = builder.percentTraining

  // A list of (source, target) training instances.
  val trainingData = builder.trainingData

  // A list of (source, target) testing instances.
  val testingData = builder.testingData

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Relation-metadata-related objects - inverses, ranges, and the like
  ////////////////////////////////////////////////////////////////////////////////////////////////

  // The relation that PRA is learning a model for.
  val relation = builder.relation

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
  val relationInverses = builder.relationInverses

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
  val unallowedEdges = builder.unallowedEdges

  // If not null, and in combination with the acceptPolicy, this specifies which nodes are allowed
  // to be targets in the feature matrix.
  val allowedTargets = builder.allowedTargets

  // This is used in the driver that puts everything together, but there are also a few times where
  // we want to read a file from somewhere relative to the base directory (when that happens it's
  // most often because of poor design, and so this should probably be fixed and removed...).
  val praBase = builder.praBase
}

class PraConfigBuilder(config: PraConfig = null) {
  var relation: String = if (config != null) config.relation else null
  var graph: Option[Graph] = if (config != null) config.graph else None
  var allData: Dataset = if (config != null) config.allData else null
  var percentTraining: Double = if (config != null) config.percentTraining else -1.0
  var trainingData: Dataset = if (config != null) config.trainingData else null
  var testingData: Dataset = if (config != null) config.testingData else null
  var allowedTargets: Set[Int] = if (config != null) config.allowedTargets else null
  var unallowedEdges: Seq[Int] = if (config != null) config.unallowedEdges else Seq()
  var relationInverses: Map[Int, Int] = if (config != null) config.relationInverses else null
  var outputBase: String = if (config != null) config.outputBase else null
  var outputMatrices: Boolean = if (config != null) config.outputMatrices else false
  var outputter: Outputter = if (config != null) config.outputter else null
  var praBase: String = if (config != null) config.praBase else null

  var noChecks = false

  def setRelation(relation: String) = {this.relation = relation; this;}
  def setGraph(graph: Graph) = {this.graph = Some(graph);this;}
  def setAllData(d: Dataset) = {this.allData = d;this;}
  def setPercentTraining(p: Double) = {this.percentTraining = p;this;}
  def setTrainingData(t: Dataset) = {this.trainingData = t;this;}
  def setTestingData(t: Dataset) = {this.testingData = t;this;}
  def setAllowedTargets(a: Set[Int]) = {this.allowedTargets = a;this;}
  def setUnallowedEdges(e: Seq[Int]) = {this.unallowedEdges = e;this;}
  def setRelationInverses(i: Map[Int, Int]) = {relationInverses = i;this;}
  def setOutputBase(outputBase: String) = {this.outputBase = outputBase;this;}
  def setOutputter(o: Outputter) = {this.outputter = o;this;}
  def setOutputMatrices(o: Boolean) = {this.outputMatrices = o;this;}
  def setPraBase(o: String) = {this.praBase = o;this;}

  def build(): PraConfig = {
    if (outputter == null) {
      outputter = new Outputter(null, new FileUtil())
    }
    if (noChecks) return new PraConfig(this)

    // Check that we have a consistent state, with everything specified that is necessary for
    // running PRA.  TODO(matt): actually, I'm not sure this is relevant anymore...  Should I
    // just remove these checks?  Maybe it's just better to have tests that make sure everything
    // is set up right in all of the various code paths...
    if (relation == null) throw new IllegalStateException("relation must be set")
    if (allData != null && percentTraining == -1.0) throw new IllegalStateException(
        "Must give percent training when specifying allData")
    if (allData != null && trainingData != null) throw new IllegalStateException(
        "allData and trainingData are mutually exclusive")
    if (allData == null && trainingData == null) throw new IllegalStateException(
        "Must specify either allData or trainingData")
    new PraConfig(this)
  }

  /**
   * Disables consistency checks. This can be used if you're building an intermediate Builder
   * object (as in KbPraDriver.java or Driver.scala), or if you're just writing a simple test of
   * something that needs part of a PraConfig, but not all of it.
   */
  def setNoChecks() = {
    noChecks = true
    this
  }
}
