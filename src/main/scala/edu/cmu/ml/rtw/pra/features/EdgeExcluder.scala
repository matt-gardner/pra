package edu.cmu.ml.rtw.pra.features

import edu.cmu.graphchi.preprocessing.VertexIdTranslate
import com.mattg.util.Pair

import scala.collection.JavaConverters._

trait EdgeExcluder {
  def prepUnallowedWalks(vertexIdTranslate: VertexIdTranslate)
  def shouldExcludeEdge(sourceVertex: Int, nextVertex: Int, atVertex: Vertex, edgeType: Int): Boolean
}

class SingleEdgeExcluder(val edgesToExclude: Seq[((Int, Int), Int)]) extends EdgeExcluder {
  // edgesToExclude uses the _graph node ids_ as values in the list.  unallowedWalks uses the
  // _graphchi internal translated node ids_.  GraphChi makes its own mapping of the node ids to
  // make for more efficient processing.
  var unallowedWalks: Map[Int, Map[Int, Seq[Int]]] = null

  // Sets up the edges that should be excluded for easier searching.  It really would be nice to
  // have the translate object as a constructor parameter; this would be a lot cleaner.  But that
  // would mean constructing this in the PathFinder or the PathFollower, and that seems ugly to
  // me...
  override def prepUnallowedWalks(translator: VertexIdTranslate) {
    unallowedWalks = edgesToExclude.flatMap(e => {
        val source = translator.forward(e._1._1)
        val target = translator.forward(e._1._2)
        val edge = e._2
        Seq((source, target, edge), (target, source, edge))
      }).groupBy(_._1).mapValues(edgeSequence => {
          edgeSequence.map(x => (x._2, x._3)).groupBy(_._1).mapValues(_.map(_._2))
      })
  }

  override def shouldExcludeEdge(
      sourceVertex: Int,
      nextVertex: Int,
      currentVertex: Vertex,
      edgeType: Int): Boolean = {
    // We can use edges of the relation we're predicting as long as they aren't the instance we're
    // starting the walk from.  So only exclude the walk if the source vertex is the same as the
    // current vertex or the next vertex
    if (sourceVertex != currentVertex.getId() && sourceVertex != nextVertex) return false
    // Now we look in our unallowedWalks map and see if the edge is there between the current
    // vertex and the next vertex.  It doesn't matter which vertex we use as the source here,
    // because we put both directions into the unallowedWalks map.
    val sourceMap = unallowedWalks.getOrElse(currentVertex.getId(), null)
    if (sourceMap == null) return false
    val unallowedEdges = sourceMap.getOrElse(nextVertex, null)
    if (unallowedEdges == null) return false
    // If we've made it here, we actually have a (source, target) pair that was given to us in the
    // edgesToExclude parameter.  Now we have to check if the edge we're trying to walk on should
    // be excluded.
    val edgeCount = unallowedEdges.count(_ == edgeType)
    // If the edge didn't show up at all, we're good.
    if (edgeCount == 0) return false
    // But if it did show up, we still might be ok, if there are more edges between the source and
    // target node than just the ones we wanted to exclude.  This shouldn't happen with standard KB
    // edges, but if the edges are embedded into a latent space, there could be other edges that
    // have the same representation, so we have to check the counts here.
    val targetCount = currentVertex.getNodeEdgeCount(nextVertex, edgeType)
    if (edgeCount >= targetCount) return true
    return false
  }
}

object SingleEdgeExcluder {
  def fromJava(javaEdgesToExclude: java.util.List[Pair[Pair[Integer, Integer], Integer]]) = {
    val edgesToExclude = javaEdgesToExclude.asScala.map(edge => {
      ((edge.getLeft().getLeft().toInt, edge.getLeft().getRight().toInt), edge.getRight().toInt)
    }).toSeq
    new SingleEdgeExcluder(edgesToExclude)
  }
}
