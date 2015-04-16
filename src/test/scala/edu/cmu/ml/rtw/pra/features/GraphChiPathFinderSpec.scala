package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.DatasetFactory
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

class GraphChiPathFinderSpec extends FlatSpecLike with Matchers {
  val factory = new FakePathTypeFactory()
  val inverses = Map(1 -> 2)
  val path1 = factory.fromString("-1-2-3-")
  val path2 = factory.fromString("-1-2-3- INVERSE")
  val pathCounts = Map(path1 -> 2, path2 -> 2).mapValues(x => Integer.valueOf(x)).asJava
  val pair = Pair.makePair(1:Integer, 1:Integer)

  "collapseInverses" should "collapse inverses" in {
    val collapsed = GraphChiPathFinder.collapseInverses(pathCounts, inverses, factory)
    collapsed.size should be(1)
    collapsed.get(path2) should be(4)
  }

  "collapseInversesInCountMap" should "collapse inverses in count map" in {
    val collapsed = GraphChiPathFinder.collapseInversesInCountMap(Map(pair -> pathCounts).asJava, inverses, factory)
    collapsed.size should be(1)
    collapsed.get(pair).get(path2) should be(4)
  }

}
