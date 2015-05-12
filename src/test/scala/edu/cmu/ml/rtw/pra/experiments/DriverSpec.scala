package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.users.matt.util.Dictionary
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

class DriverSpec extends FlatSpecLike with Matchers {
  val relation = "testRelation"
  val relation2 = "testRelationNoInverse"
  val inverse = "testInverse"
  val embedded1 = "embedded1"
  val embedded2 = "embedded2"
  val embedded3 = "embedded3"
  val edgeDict = new Dictionary()
  val inverses = Map(edgeDict.getIndex(relation) -> edgeDict.getIndex(inverse))
  val embeddings = Map(relation -> List(embedded1, embedded2), inverse -> List(embedded2, embedded3))

  val splitsDirectory = "/dev/null/"
  val kbDirectory = "/dev/null/"
  val fixedSplitRelation = "/test/fb/relation"
  val crossValidatedRelation = "/CV/fb/relation"
  val builder = new PraConfig.Builder()
  val fileUtil = new FakeFileUtil()
  val driver = new Driver("/", fileUtil)

  def expectCount[T](collection: Seq[T], element: T, count: Int) {
    val actualCount = collection.filter(_.equals(element)).size
    actualCount should be(count)
  }

  "createUnallowedEdges" should "work with both inverses and embeddings" in {
    // Test the general case, with inverses and embeddings.  Note that we should always include
    // the relation itself, even if there are embeddings.  Sometimes we use both the original
    // edge and the embedding as edges in the graph.  If the original edge is still there, this
    // will stop it, and if it's not there, having it in this list won't hurt anything.
    val unallowedEdges = Driver.createUnallowedEdges(relation, inverses, embeddings, edgeDict)
    unallowedEdges.size should be(6)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded1), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded2), 2)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded3), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(inverse), 1)
  }

  it should "work with no inverses" in {
    val unallowedEdges = Driver.createUnallowedEdges(relation, Map(), embeddings, edgeDict)
    unallowedEdges.size should be(3)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded1), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded2), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
  }

  it should "work with no embeddings" in {
    val unallowedEdges = Driver.createUnallowedEdges(relation, inverses, null, edgeDict)
    unallowedEdges.size should be(2)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(inverse), 1)
  }

  it should "work when the relation has no inverse" in {
    val unallowedEdges = Driver.createUnallowedEdges(relation2, inverses, null, edgeDict)
    unallowedEdges.size should be(1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation2), 1)
  }

  it should "work when the inverse has no embeddings" in {
    // This test is due to a bug.
    val unallowedEdges = Driver.createUnallowedEdges(relation, inverses, embeddings - inverse, edgeDict)
    unallowedEdges.size should be(4)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded1), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded2), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(inverse), 1)
  }

  it should "work when the relation has no embeddings" in {
    // This test is also due to a bug.
    val unallowedEdges = Driver.createUnallowedEdges(relation, inverses, Map(), edgeDict)
    unallowedEdges.size should be(2)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(inverse), 1)
  }

  "initializeSplit" should "not do cross validation when there's a fixed split" in {
    fileUtil.addExistingFile(splitsDirectory + fixedSplitRelation.replace("/", "_"))
    fileUtil.addFileToBeRead(splitsDirectory + fixedSplitRelation.replace("/", "_") + "/training.tsv", "")
    fileUtil.addFileToBeRead(splitsDirectory + fixedSplitRelation.replace("/", "_") + "/testing.tsv", "")
    Driver.initializeSplit(splitsDirectory, kbDirectory, fixedSplitRelation, builder,
      fileUtil) should be(false)
  }

  it should "do cross validation when there's no fixed split" in {
    fileUtil.setDoubleList(List(java.lang.Double.valueOf(0.0)).asJava)
    fileUtil.addFileToBeRead(splitsDirectory + "relations/" + crossValidatedRelation.replace("/", "_"), "")
    Driver.initializeSplit(splitsDirectory, kbDirectory, crossValidatedRelation, builder,
      fileUtil) should be(true)
  }
}
