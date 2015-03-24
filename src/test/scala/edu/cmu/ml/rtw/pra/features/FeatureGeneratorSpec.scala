package edu.cmu.ml.rtw.pra.features

import java.io.BufferedReader
import java.io.StringReader

import org.scalatest._

import scala.collection.JavaConverters._

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.DatasetFactory
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.Pair
import edu.cmu.ml.rtw.users.matt.util.TestUtil

class FeatureGeneratorSpec extends FlatSpecLike with Matchers {
  val factory = new FakePathTypeFactory()
  val generator =
    new FeatureGenerator(new PraConfig.Builder().noChecks().setPathTypeFactory(factory).build())
  val path1 = factory.fromString("-1-2-3-")
  val path2 = factory.fromString("-1-2-3- INVERSE")
  val pathCounts = Map(path1 -> 2, path2 -> 2)
  val inverses = Map(1 -> 2)
  val pair = (1, 1)

  val node1 = "node1"
  val node2 = "node2"
  val node3 = "node3"
  val node4 = "node4"
  val nodeDict = new Dictionary()
  val node1Index = nodeDict.getIndex(node1)
  val node2Index = nodeDict.getIndex(node2)
  val node3Index = nodeDict.getIndex(node3)
  val node4Index = nodeDict.getIndex(node4)
  val unallowedEdges = List(1, 3, 2).map(x => java.lang.Integer.valueOf(x)).asJava
  val dataFile = node1 + "\t" + node2 + "\n" + node3 + "\t" + node4 + "\n"
  val data = new DatasetFactory().fromReader(new BufferedReader(new StringReader(dataFile)), new Dictionary())
  val config = new PraConfig.Builder().noChecks().setUnallowedEdges(unallowedEdges).build()
  val edgesToExcludeGenerator = new FeatureGenerator(config)

  "collapseInverses" should "collapse inverses" in {
    val collapsed = generator.collapseInverses(pathCounts, inverses)
    collapsed.size should be(1)
    collapsed(path2) should be(4)
  }

  "collapseInversesInCountMap" should "collapse inverses in count map" in {
    val collapsed = generator.collapseInversesInCountMap(Map(pair -> pathCounts), inverses)
    collapsed.size should be(1)
    collapsed(pair)(path2) should be(4)
  }

  "createEdgesToExclude" should "handle the basic case" in {
    val edgesToExclude = edgesToExcludeGenerator.createEdgesToExclude(data)
    edgesToExclude.size should be(6)
    edgesToExclude.count(_ == ((node1Index, node2Index), 1)) should be(1)
    edgesToExclude.count(_ == ((node1Index, node2Index), 3)) should be(1)
    edgesToExclude.count(_ == ((node1Index, node2Index), 2)) should be(1)
    edgesToExclude.count(_ == ((node3Index, node4Index), 1)) should be(1)
    edgesToExclude.count(_ == ((node3Index, node4Index), 3)) should be(1)
    edgesToExclude.count(_ == ((node3Index, node4Index), 2)) should be(1)
  }

  it should "work with a null dataset" in {
    val edgesToExclude = edgesToExcludeGenerator.createEdgesToExclude(null)
    edgesToExclude.size should be(0)
  }

  it should "work with a dataset with no targets" in {
    val nodes = List(Integer.valueOf(node1Index), Integer.valueOf(node3Index)).asJava
    val data = new Dataset.Builder().setPositiveSources(nodes).build()
    val edgesToExclude = edgesToExcludeGenerator.createEdgesToExclude(data)
    edgesToExclude.size should be(0)
  }
}

