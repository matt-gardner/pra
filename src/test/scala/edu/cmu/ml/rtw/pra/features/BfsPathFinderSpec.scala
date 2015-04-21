package edu.cmu.ml.rtw.pra.features

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.collection.JavaConverters._

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil
import edu.cmu.ml.rtw.users.matt.util.Pair

class BfsPathFinderSpec extends FlatSpecLike with Matchers {

  val fileUtil = new FakeFileUtil

  val graphFilename = "/graph file"
  val graphFileContents = "1\t2\t1\n" +
      "1\t3\t1\n" +
      "1\t4\t2\n" +
      "2\t1\t4\n" +
      "5\t1\t3\n" +
      "6\t1\t1\n"

  fileUtil.addFileToBeRead(graphFilename, graphFileContents)

  val positiveSources = Seq(5:Integer).asJava
  val positiveTargets = Seq(3:Integer).asJava
  val negativeSources = Seq[Integer]().asJava
  val negativeTargets = Seq[Integer]().asJava
  val data = new Dataset(positiveSources, positiveTargets, negativeSources, negativeTargets)
  val config = new PraConfig.Builder()
    .setUnallowedEdges(Seq(1:Integer).asJava).setGraph(graphFilename).noChecks().build()
  config.nodeDict.getIndex("node1")
  config.nodeDict.getIndex("node2")
  config.nodeDict.getIndex("node3")
  config.nodeDict.getIndex("node4")
  config.nodeDict.getIndex("node5")
  config.nodeDict.getIndex("node6")

  val params: JValue = JNothing

  def makeFinder() = new BfsPathFinder(params, config, "/", fileUtil)

  "loadGraph" should "correctly read in a graph" in {
    val graph = makeFinder().loadGraph(graphFilename, 7)
    graph.size should be(7)
    graph(0) should be(Map())
    graph(1).size should be(4)
    graph(1)(1)._1.size should be(1)
    graph(1)(1)._1(0) should be(6)
    graph(1)(1)._2.size should be(2)
    graph(1)(1)._2(0) should be(2)
    graph(1)(1)._2(1) should be(3)
    graph(1)(2)._1.size should be(0)
    graph(1)(2)._2.size should be(1)
    graph(1)(2)._2(0) should be(4)
    graph(1)(3)._1.size should be(1)
    graph(1)(3)._1(0) should be(5)
    graph(1)(3)._2.size should be(0)
    graph(1)(4)._1.size should be(1)
    graph(1)(4)._1(0) should be(2)
    graph(1)(4)._2.size should be(0)
    graph(2).size should be(2)
    graph(2)(1)._1.size should be(1)
    graph(2)(1)._1(0) should be(1)
    graph(2)(1)._2.size should be(0)
    graph(2)(4)._1.size should be(0)
    graph(2)(4)._2.size should be(1)
    graph(2)(4)._2(0) should be(1)
    graph(3).size should be(1)
    graph(3)(1)._1.size should be(1)
    graph(3)(1)._1(0) should be(1)
    graph(3)(1)._2.size should be(0)
    graph(4).size should be(1)
    graph(4)(2)._1.size should be(1)
    graph(4)(2)._1(0) should be(1)
    graph(4)(2)._2.size should be(0)
    graph(5).size should be(1)
    graph(5)(3)._1.size should be(0)
    graph(5)(3)._2.size should be(1)
    graph(5)(3)._2(0) should be(1)
    graph(6).size should be(1)
    graph(6)(1)._1.size should be(0)
    graph(6)(1)._2.size should be(1)
    graph(6)(1)._2(0) should be(1)
  }

  "findPaths" should "find correct subgraphs with simple parameters" in {
    val factory = new BasicPathTypeFactory
    val finder = makeFinder()
    finder.findPaths(config, data, Seq(((1, 3), 1)))
    val results53 = finder.results((5, 3)).asScala

    // 6 Source paths
    results53(factory.fromString("-3-")).size should be(1)
    results53(factory.fromString("-3-")) should contain(Pair.makePair(5, 1))
    results53(factory.fromString("-3-1-")).size should be(2)
    results53(factory.fromString("-3-1-")) should contain(Pair.makePair(5, 2))
    results53(factory.fromString("-3-1-")) should contain(Pair.makePair(5, 3))
    results53(factory.fromString("-3-2-")).size should be(1)
    results53(factory.fromString("-3-2-")) should contain(Pair.makePair(5, 4))
    results53(factory.fromString("-3-_4-")).size should be(1)
    results53(factory.fromString("-3-_4-")) should contain(Pair.makePair(5, 2))
    results53(factory.fromString("-3-_1-")).size should be(1)
    results53(factory.fromString("-3-_1-")) should contain(Pair.makePair(5, 6))
    results53(factory.fromString("-3-_3-")).size should be(1)
    results53(factory.fromString("-3-_3-")) should contain(Pair.makePair(5, 5))

    // 6 Target paths
    results53(factory.fromString("-_1-")).size should be(1)
    results53(factory.fromString("-_1-")) should contain(Pair.makePair(3, 1))
    results53(factory.fromString("-_1-1-")).size should be(2)
    results53(factory.fromString("-_1-1-")) should contain(Pair.makePair(3, 2))
    results53(factory.fromString("-_1-1-")) should contain(Pair.makePair(3, 3))
    results53(factory.fromString("-_1-2-")).size should be(1)
    results53(factory.fromString("-_1-2-")) should contain(Pair.makePair(3, 4))
    results53(factory.fromString("-_1-_4-")).size should be(1)
    results53(factory.fromString("-_1-_4-")) should contain(Pair.makePair(3, 2))
    results53(factory.fromString("-_1-_1-")).size should be(1)
    results53(factory.fromString("-_1-_1-")) should contain(Pair.makePair(3, 6))
    results53(factory.fromString("-_1-_3-")).size should be(1)
    results53(factory.fromString("-_1-_3-")) should contain(Pair.makePair(3, 5))

    // 7 Combined paths - some of these contain loops.  If loop detection is ever added, these
    // tests should be revisited.
    results53(factory.fromString("-3-1-_1-1-")).size should be(1)
    results53(factory.fromString("-3-1-_1-1-")) should contain(Pair.makePair(5, 3))
    results53(factory.fromString("-3-1-4-1-")).size should be(1)
    results53(factory.fromString("-3-1-4-1-")) should contain(Pair.makePair(5, 3))
    results53(factory.fromString("-3-2-_2-1-")).size should be(1)
    results53(factory.fromString("-3-2-_2-1-")) should contain(Pair.makePair(5, 3))
    results53(factory.fromString("-3-_4-4-1-")).size should be(1)
    results53(factory.fromString("-3-_4-4-1-")) should contain(Pair.makePair(5, 3))
    results53(factory.fromString("-3-_4-_1-1-")).size should be(1)
    results53(factory.fromString("-3-_4-_1-1-")) should contain(Pair.makePair(5, 3))
    results53(factory.fromString("-3-_1-1-1-")).size should be(1)
    results53(factory.fromString("-3-_1-1-1-")) should contain(Pair.makePair(5, 3))
    results53(factory.fromString("-3-_3-3-1-")).size should be(1)
    results53(factory.fromString("-3-_3-3-1-")) should contain(Pair.makePair(5, 3))

    // 6 + 6 + 7 = 19 total paths
    results53.size should be(19)
  }

  it should "exclude edges when specified" in {
    val data = new Dataset(Seq(1:Integer).asJava, positiveTargets, negativeSources, negativeTargets)
    val factory = new BasicPathTypeFactory
    val finder = makeFinder()
    finder.findPaths(config, data, Seq(((1, 3), 1)))
    val results13 = finder.results((1, 3)).asScala
    val badPath = factory.fromString("-1-")
    results13(badPath) should not contain(Pair.makePair(1, 3))
  }
}
