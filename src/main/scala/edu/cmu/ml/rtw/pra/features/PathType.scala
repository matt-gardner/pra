package edu.cmu.ml.rtw.pra.features

import edu.cmu.ml.rtw.pra.graphs.Graph

trait PathType {

  /**
   * How many iterations should we run if we want to be sure we complete the computation for this
   * path type?
   */
  def recommendedIters(): Int

  /**
   * Is this the last hop?  This determines whether the walk gets sent to the companion for
   * tracking statistics.
   */
  def isLastHop(hopNum: Int): Boolean

  /**
   * Creates a machine-parseable representation of the PathType.  This must match the
   * ObjectParser[PathType] implementation in the PathTypeFactory.  Edges types, node types, and
   * whatever else, are encoded as integers.
   */
  def encodeAsString(): String

  /**
   * Creates a human-digestable representation of the PathType.  To make it human readable, we need
   * to convert the integers that show up in the path type into their string representations, using
   * the provided dictionaries.
   */
  def encodeAsHumanReadableString(graph: Graph, edgeMap: Map[Int, String] = Map()): String

  /**
   * Given the hop number and information about the current vertex, pick an edge to follow.
   *
   * The EdgeExcluder is an object that uses some global or external information to determine if
   * the walk is not allowed, generally because it's using an edge from the test data, or an edge
   * that we're trying to learn.
   *
   * It is advisable, if at all possible, to do whatever you can to avoid looping over all of the
   * edges in this method.  This is in the inner loop of the PathFollower, and so it will get run a
   * _lot_.  Even worse, the processing is distributed across threads by vertex, and if you loop
   * over all of the edges, you make the amount of computation done by each thread more uneven,
   * because vertices that have lots of edges tend to get more walks at them.  So, really, try hard
   * to avoid a loop over all of the edges.  The cache parameter should be helpful for that.
   */
  def nextHop(
    hopNum: Int,
    sourceVertex: Int,
    vertex: Vertex,
    random: java.util.Random,
    edgeExcluder: EdgeExcluder,
    cache: PathTypeVertexCache): Int

  def cacheVertexInformation(vertex: Vertex, hopNum: Int): PathTypeVertexCache
}

