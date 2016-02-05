package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.data.Instance
import edu.cmu.ml.rtw.pra.data.NodeInstance
import edu.cmu.ml.rtw.pra.data.NodePairInstance

// A FeatureMatcher is constructed (generally) from a single feature, and can filter a graph to
// find subgraphs that would have generated the feature.  This is very similar to the old
// PathFollower logic, except we're not worrying about computing probabilities here, just finding
// whether a particular node or node pair matches a feature.
//
// The use case is, say, given an entity and a set of features (assuming NodePairInstances), find a
// set of entities that will have non-zero probability in a model using these features.  For
// NodeInstances instead of NodePairInstances, you don't generally have as good a starting place,
// so you might have to iterate over all instances in the graph, which is why this is only
// currently implemented for NodePairInstances.
//
// TODO(matt): Do I need the type parameter here?  I don't currently use it in any of the methods
// in this class.  I guess it's marginally useful to ensure type consistency with the dataset it's
// used with, but that's about it.
trait FeatureMatcher[T <: Instance] {
  // If we've gone stepsTaken steps, have we matched the whole feature?  stepsTaken may not be
  // sufficient for some features to tell if they're finished, but it is enough for all of the
  // features I plan on implementing right now.  The rest will have to wait, and get a more
  // complicated API.
  def isFinished(stepsTaken: Int): Boolean

  // Is this edge type acceptable after stepsTaken steps, according to this feature?  If this is a
  // plain PRA-style feature, this will just say if the edgeId matches step stepsTaken in the path
  // type.  More complicated features may have more complicated logic here (or just always return
  // true).
  def edgeOk(edgeId: Int, stepsTaken: Int): Boolean

  // Is this node acceptable after stepsTaken steps?  For PRA-style features, this will always
  // return true, but some feature types need to check this.
  def nodeOk(nodeId: Int, stepsTaken: Int): Boolean
}

object EmptyNodePairFeatureMatcher extends FeatureMatcher[NodePairInstance] {
  override def isFinished(stepsTaken: Int) = true
  override def edgeOk(edgeId: Int, stepsTaken: Int) = false
  override def nodeOk(nodeId: Int, stepsTaken: Int) = false
}

object EmptyNodeFeatureMatcher extends FeatureMatcher[NodeInstance] {
  override def isFinished(stepsTaken: Int) = true
  override def edgeOk(edgeId: Int, stepsTaken: Int) = false
  override def nodeOk(nodeId: Int, stepsTaken: Int) = false
}
