package edu.cmu.ml.rtw.pra.graphs

import org.scalatest._

import edu.cmu.graphchi.walks.IntWalkArray
import edu.cmu.graphchi.walks.IntWalkManager

import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.experiments.Instance
import edu.cmu.ml.rtw.pra.features.FakeChiVertex
import edu.cmu.ml.rtw.pra.features.FakeIntDrunkardContext
import edu.cmu.ml.rtw.users.matt.util.FakeRandom
import edu.cmu.ml.rtw.users.matt.util.TestUtil
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function

import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods._

class PprNegativeExampleSelectorSpec extends FlatSpecLike with Matchers {
  val params: JValue = JNothing
  val dataset = new Dataset(Seq(new Instance(1, 2, true)))

  "selectNegativeExamples" should "just add the sampled negatives to the given data" in {
    val selector = new PprNegativeExampleSelector(params, "", 1) {
      override def computePersonalizedPageRank(data: Dataset, allowedSources: Set[Int],
          allowedTargets: Set[Int]) = {
        Map[Int, Map[Int, Int]]()
      }
      override def sampleByPrr(data: Dataset, pprValues: Map[Int, Map[Int, Int]]) = {
        Seq((1, 1), (2, 2), (3, 3))
      }
    }
    val withNegatives = selector.selectNegativeExamples(dataset, Set(), Set())
    withNegatives.getPositiveInstances should be(Seq(new Instance(1, 2, true)))
    withNegatives.getNegativeInstances should be(Seq(new Instance(1, 1, false),
      new Instance(2, 2, false), new Instance(3, 3, false)))
  }

  "sampleByPrr" should "get enough negatives per positive" in {
    val selector = new PprNegativeExampleSelector(params, "", 1) {
      var index = -1
      var source = false
      override def weightedSample(list: Array[(Int, Int)], weight: Double, default: Int) = {
        if (source == false) index += 1
        source = !source
        list(index)._1
      }
    }
    val pprValues = Map(1 -> Map(3 -> 1, 4 -> 1, 5 -> 1), 2 -> Map(3 -> 1, 4 -> 1, 5 -> 1))
    val negatives = selector.sampleByPrr(dataset, pprValues)
    negatives.size should be(3)
    negatives.toSet should be(Set((3, 3), (4, 4), (5, 5)))
  }

  it should "give up when there aren't enough" in {
    val selector = new PprNegativeExampleSelector(params, "", 1) {
      override def weightedSample(list: Array[(Int, Int)], weight: Double, default: Int) = {
        1
      }
    }
    val pprValues = Map(1 -> Map(3 -> 1, 4 -> 1, 5 -> 1), 2 -> Map(3 -> 1, 4 -> 1, 5 -> 1))
    val negatives = selector.sampleByPrr(dataset, pprValues)
    negatives.size should be(1)
    negatives.toSet should be(Set((1, 1)))
  }

  it should "exclude training data" in {
    val selector = new PprNegativeExampleSelector(params, "", 1) {
      var index = -1
      var source = false
      override def weightedSample(list: Array[(Int, Int)], weight: Double, default: Int) = {
        if (source == false) index += 1
        source = !source
        if (index >= list.size) index = 0
        list(index)._1
      }
    }
    val pprValues = Map(1 -> Map(1 -> 1), 2 -> Map(2 -> 1))
    val negatives = selector.sampleByPrr(dataset, pprValues)
    negatives.size should be(0)
  }

  "weightedSample" should "give the right samples" in {
    val random = new FakeRandom
    val selector = new PprNegativeExampleSelector(params, "", 1, random)
    val list = Seq((1, 1), (2, 1), (3, 1)).toArray
    random.setNextDouble(.1)
    selector.weightedSample(list, 3.0, -1) should be(1)
    random.setNextDouble(.4)
    selector.weightedSample(list, 3.0, -1) should be(2)
    random.setNextDouble(.9)
    selector.weightedSample(list, 3.0, -1) should be(3)
    random.setNextDouble(.9)
    selector.weightedSample(list, 6.0, -1) should be(-1)
  }

  "processWalksAtVertex" should "move walks the right way" in {
    val manager = new IntWalkManager(1, 1)
    val context = new FakeIntDrunkardContext()
    val random = new FakeRandom()
    val chiVertex = new FakeChiVertex(1);
    chiVertex.addOutEdge(5, 2);
    chiVertex.addOutEdge(5, 1);
    chiVertex.addOutEdge(2, 1);
    chiVertex.addInEdge(1, 1);
    chiVertex.addInEdge(3, 2);
    val walk = manager.reencodeWalk(0, 1, false)
    val walkArray = new IntWalkArray(Array(walk))
    val selector = new PprNegativeExampleSelector(params, "", 1, random)

    random.setNextInt(1)
    random.setNextDouble(0.99)
    context.setExpectations(false, walk, 5, true)
    selector.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()

    random.setNextInt(2)
    context.setExpectations(false, walk, 2, true)
    selector.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()

    random.setNextInt(3)
    context.setExpectations(false, walk, 1, true)
    selector.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()

    random.setNextInt(3)
    random.setNextDouble(0.001)
    context.setExpectationsForReset(walk, false)
    selector.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()
  }

  it should "reset on an empty vertex" in {
    val context = new FakeIntDrunkardContext()
    val random = new FakeRandom()
    val chiVertex = new FakeChiVertex(1);
    val walk = 2
    val walkArray = new IntWalkArray(Array(walk))
    val selector = new PprNegativeExampleSelector(params, "", 1, random)

    random.setNextInt(1)
    random.setNextDouble(0.99)
    context.setExpectationsForReset(walk, false)
    selector.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()
  }

  "computePersonalizedPageRank" should "actually work..." in {
    val selector = new PprNegativeExampleSelector(params, "src/test/resources/edges.tsv", 1)
    val pprValues = selector.computePersonalizedPageRank(dataset, Set(4, 7, 10), Set(3, 5))
    pprValues(1).size should be >= 2
    pprValues(1)(4) should be > pprValues(1)(7)
    pprValues(1)(7) should be > pprValues(1).getOrElse(10, 0)
    pprValues(2).size should be (2)
    pprValues(2)(3) should be > pprValues(2)(5)
    // TODO(matt): there were some bugs caused because I wasn't using vertexIdTranslate right.  It
    // would be nice to be able to test that well, but it would require overriding the
    // DrunkardMobEngine's getVertexIdTranslate method, and it's a little tricky to inject that
    // dependency into this method.  GraphChi just ends up using an identity for the
    // vertexIdTranslate with the graph I'm testing with here, so we can't easily check for these
    // problems...
  }
}
