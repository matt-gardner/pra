package edu.cmu.ml.rtw.pra.graphs

import org.scalatest._

import edu.cmu.graphchi.walks.IntWalkArray
import edu.cmu.graphchi.walks.IntWalkManager

import edu.cmu.ml.rtw.pra.data.Dataset
import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.FakeChiVertex
import edu.cmu.ml.rtw.pra.features.FakeIntDrunkardContext
import com.mattg.util.FakeRandom

import org.json4s._

class GraphChiPprComputerSpec extends FlatSpecLike with Matchers {
  val params: JValue = JNothing
  val outputter = Outputter.justLogger
  val graph = new GraphOnDisk("src/test/resources/", outputter)
  val random = new FakeRandom()
  val dataset = new Dataset[NodePairInstance](Seq(new NodePairInstance(1, 2, true, null)))
  val sources = dataset.instances.map(_.source).toSet
  val targets = dataset.instances.map(_.target).toSet

  "processWalksAtVertex" should "move walks the right way" in {
    val manager = new IntWalkManager(1, 1)
    val context = new FakeIntDrunkardContext()
    val chiVertex = new FakeChiVertex(1);
    chiVertex.addOutEdge(5, 2);
    chiVertex.addOutEdge(5, 1);
    chiVertex.addOutEdge(2, 1);
    chiVertex.addInEdge(1, 1);
    chiVertex.addInEdge(3, 2);
    val walk = manager.reencodeWalk(0, 1, false)
    val walkArray = new IntWalkArray(Array(walk))
    val computer = new GraphChiPprComputer(params, graph, random)

    random.setNextInt(1)
    random.setNextDouble(0.99)
    context.setExpectations(false, walk, 5, true)
    computer.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()

    random.setNextInt(2)
    context.setExpectations(false, walk, 2, true)
    computer.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()

    random.setNextInt(3)
    context.setExpectations(false, walk, 1, true)
    computer.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()

    random.setNextInt(3)
    random.setNextDouble(0.001)
    context.setExpectationsForReset(walk, false)
    computer.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()
  }

  it should "reset on an empty vertex" in {
    val context = new FakeIntDrunkardContext()
    val chiVertex = new FakeChiVertex(1);
    val walk = 2
    val walkArray = new IntWalkArray(Array(walk))
    val computer = new GraphChiPprComputer(params, graph, random)

    random.setNextInt(1)
    random.setNextDouble(0.99)
    context.setExpectationsForReset(walk, false)
    computer.processWalksAtVertex(walkArray, chiVertex, context, random)
    context.testFinished()
  }

  "computePersonalizedPageRank" should "actually work..." in {
    val computer = new GraphChiPprComputer(params, graph, random)
    val pprValues = computer.computePersonalizedPageRank(sources, targets, Some(Set(4, 7, 10)), Some(Set(3, 5)))
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

class InMemoryPprComputerSpec extends FlatSpecLike with Matchers {
  val params: JValue = JNothing
  val outputter = Outputter.justLogger
  val graph = new GraphOnDisk("src/test/resources/", outputter)
  val random = new FakeRandom()
  val dataset = new Dataset[NodePairInstance](Seq(new NodePairInstance(1, 2, true, null)))
  val sources = dataset.instances.map(_.source).toSet
  val targets = dataset.instances.map(_.target).toSet

  "computePersonalizedPageRank" should "actually work..." in {
    val computer = new InMemoryPprComputer(params, graph, outputter)
    val pprValues = computer.computePersonalizedPageRank(sources, targets, Some(Set(4, 7, 10)), Some(Set(3, 5)))
    pprValues(1).size should be >= 2
    pprValues(1)(4) should be > pprValues(1)(7)
    pprValues(1)(7) should be > pprValues(1).getOrElse(10, 0)
    pprValues(2).size should be (2)
    pprValues(2)(3) should be > pprValues(2)(5)
  }
}
