package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import scala.collection.JavaConverters._

class EdgeExcluderSpec extends FlatSpecLike with Matchers {

  "prepUnallowedWalks" should "work correctly" in {
    val edgesToExclude = Seq(((1, 2), 1), ((2, 3), 1), ((2, 3), 1))
    val excluder = new SingleEdgeExcluder(edgesToExclude)
    excluder.prepUnallowedWalks(new FakeVertexIdTranslate())
    println(excluder.unallowedWalks)
    excluder.unallowedWalks.size should be(3)
    excluder.unallowedWalks(1)(2).count(_ == 1) should be(1)
    excluder.unallowedWalks(2)(1).count(_ == 1) should be(1)
    excluder.unallowedWalks(2)(3).count(_ == 1) should be(2)
    excluder.unallowedWalks(3)(2).count(_ == 1) should be(2)
  }

  "shouldExcludeEdge" should "exclude edge when source and target match" in {
    val edgesToExclude = Seq(((1, 2), 1))
    val excluder = new SingleEdgeExcluder(edgesToExclude)
    excluder.prepUnallowedWalks(new FakeVertexIdTranslate)
    val chiVertex = new FakeChiVertex(1)
    chiVertex.addInEdge(3, 3);
    chiVertex.addInEdge(2, 1);
    chiVertex.addOutEdge(1, 1);

    // First we test the simple cases: we should exclude the edge when the source of the walk
    // matches the edge to be excluded, and we're trying to walk across the edge.
    var vertex = new Vertex(chiVertex);
    excluder.shouldExcludeEdge(1, 2, vertex, 1) should be(true)
    excluder.shouldExcludeEdge(2, 2, vertex, 1) should be(true)

    chiVertex.setVertexId(2);
    vertex = new Vertex(chiVertex);
    excluder.shouldExcludeEdge(2, 1, vertex, 1) should be(true)
    excluder.shouldExcludeEdge(1, 1, vertex, 1) should be(true)

    // Now we test some cases where the edge shouldn't be excluded.

    // The walk is between a training (source, target) pair, but the edge type is different.
    excluder.shouldExcludeEdge(1, 1, vertex, 2) should be(false)

    // The (source, target) pair isn't in the training data.
    excluder.shouldExcludeEdge(1, 4, vertex, 1) should be(false)
    excluder.shouldExcludeEdge(3, 4, vertex, 1) should be(false)

    chiVertex.setVertexId(10);
    vertex = new Vertex(chiVertex);
    excluder.shouldExcludeEdge(10, 4, vertex, 1) should be(false)

    // And the trickiest case: there are more edges in the vertex than there are edges to
    // exclude.  This might happen if the KB edges are embedded into a latent space, and so
    // there is overlap in the edge types.  We only want to exclude one edge of each type.
    chiVertex.setVertexId(2);
    chiVertex.addOutEdge(1, 1);
    vertex = new Vertex(chiVertex);
    excluder.shouldExcludeEdge(1, 1, vertex, 1) should be(false)
  }
}
