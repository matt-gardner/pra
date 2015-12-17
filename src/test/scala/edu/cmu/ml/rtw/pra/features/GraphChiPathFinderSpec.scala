package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import scala.collection.JavaConverters._

import edu.cmu.ml.rtw.pra.data.NodePairInstance

class GraphChiPathFinderSpec extends FlatSpecLike with Matchers {
  val factory = new FakePathTypeFactory()
  val inverses = Map(1 -> 2)
  val path1 = factory.fromString("-1-2-3-")
  val path2 = factory.fromString("-1-2-3- INVERSE")
  val pathCounts = Map(path1 -> 2, path2 -> 2).mapValues(x => Integer.valueOf(x)).asJava
  val instance = new NodePairInstance(1, 1, true, null)

  "collapseInverses" should "collapse inverses" in {
    val collapsed = GraphChiPathFinder.collapseInverses(pathCounts, inverses, factory)
    collapsed.size should be(1)
    collapsed.get(path2) should be(4)
  }

  "collapseInversesInCountMap" should "collapse inverses in count map" in {
    val collapsed = GraphChiPathFinder.collapseInversesInCountMap(Map(instance -> pathCounts).asJava, inverses, factory)
    collapsed.size should be(1)
    collapsed.get(instance).get(path2) should be(4)
  }

}
