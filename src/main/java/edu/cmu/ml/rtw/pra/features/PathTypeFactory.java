package edu.cmu.ml.rtw.pra.features;

import java.util.Map;

import edu.cmu.ml.rtw.users.matt.util.ObjectParser;

/**
 * A PathTypeFactory's main job is to take a Path - a sequence of (node, edge, node, edge, ...,
 * node) and return a (set of) strings that represent the "path type" of that sequence.
 *
 * The point is that the sequence that includes nodes cannot be used as a general feature, as the
 * feature would be specific to only one row in the feature matrix (the row corresponding to the
 * first and last node in the sequence).  At the very least, you would have to drop the initial and
 * final nodes to get a useful feature.  But you could do a lot more than that.  The default is to
 * just drop all the nodes and use the edge sequence as the basic PathType.  But you could imagine
 * creating a PathTypeFactory that has a mapping from nodes to categories, and gives a sequence of
 * edges and categories.  Or one that has a mapping from edges to vectors, and returns a sequence
 * of vectors.
 */
public interface PathTypeFactory extends ObjectParser<PathType> {

  /**
   * Converts the sequences of nodes and edges into the set of abstracted PathTypes that are
   * compatible with the observed sequence.
   *
   * This uses an array instead of a List for efficiency reasons, as this is used during walks in
   * the GraphChi code.
   */
  public PathType[] encode(Path path);

  /**
   * A PathType that is an identity with regards to the concatenatePathTypes and combinePathTypes
   * operations (the latter is in the CombiningPathTypeFactory interface).
   */
  public PathType emptyPathType();

  /**
   * For use in the PathFinderCompanion, where we need to combine paths from two nodes that met at
   * an intermediate node.  pathFromTarget should be reversed, then appended to pathToSource.
   */
  public PathType concatenatePathTypes(PathType pathToSource, PathType pathFromTarget);

  /**
   * Given a PathType that may have redundantly encoded edge types, collapse the inverses into a
   * less redundant version.
   *
   * Note that the only effect this has is to reduce redundancy in the feature space.  You should
   * not rely on this inverse collapsing to handle the proper exclusion of edges (see
   * EdgeExcluder).  Depending on the graph encoding, this could also be a bad idea - the only time
   * this could actually be a good idea is when you are guaranteed to have an inverse edge for
   * every edge in the graph.  So the default handling of this method is actually an identity.  But
   * you have the option to reduce some redundancy, if you know some things about how your graph is
   * constructed.
   */
  public PathType collapseEdgeInverses(PathType pathType, Map<Integer, Integer> edgeInverses);
}
