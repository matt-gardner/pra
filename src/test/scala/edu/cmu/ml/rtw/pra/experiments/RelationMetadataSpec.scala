package edu.cmu.ml.rtw.pra.experiments

import edu.cmu.ml.rtw.pra.data.NodePairInstance
import edu.cmu.ml.rtw.pra.data.Split
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory
import edu.cmu.ml.rtw.pra.graphs.Node
import com.mattg.util.MutableConcurrentDictionary
import com.mattg.util.FakeFileUtil

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.json4s._
import org.json4s.JsonDSL._
import org.scalatest._

class RelationMetadataSpec extends FlatSpecLike with Matchers {

  val relation = "testRelation"
  val relation2 = "testRelationNoInverse"
  val inverse = "testInverse"
  val embedded1 = "embedded1"
  val embedded2 = "embedded2"
  val embedded3 = "embedded3"
  val edgeDict = new MutableConcurrentDictionary()
  val inversesFile = "/inverses/file"
  val inversesFileContents = s"${relation}\t${inverse}"
  val embeddingsFile = "/embeddings/file"
  val embeddingsFileContents =
    s"${relation}\t${embedded1}\t${embedded2}\n${inverse}\t${embedded2}\t${embedded3}"
  val embeddingsFile2 = "/embeddings/file2"
  val embeddingsFileContents2 = s"${relation}\t${embedded1}\t${embedded2}"

  val splitsDirectory = "/dev/null/"
  val kbDirectory = "/dev/null/"
  val fixedSplitRelation = "/test/fb/relation"
  val crossValidatedRelation = "/CV/fb/relation"

  edgeDict.getIndex(relation)
  edgeDict.getIndex(relation2)
  edgeDict.getIndex(fixedSplitRelation)
  edgeDict.getIndex(crossValidatedRelation)
  val graph = new GraphInMemory(Array[Node](), new MutableConcurrentDictionary, edgeDict)
  val fileUtil = new FakeFileUtil()
  fileUtil.addFileToBeRead(inversesFile, inversesFileContents)
  fileUtil.addFileToBeRead(embeddingsFile, embeddingsFileContents)
  fileUtil.addFileToBeRead(embeddingsFile2, embeddingsFileContents2)

  val outputter = Outputter.justLogger
  val split = Split.create(JString("fake"), "/base/dir/", outputter, fileUtil)

  def relationMetadataWithParams(params: JValue) = {
    new RelationMetadata(params, "/", outputter, fileUtil)
  }

  def expectCount[T](collection: Seq[T], element: T, count: Int) {
    val actualCount = collection.filter(_.equals(element)).size
    actualCount should be(count)
  }

  "createUnallowedEdges" should "work with both inverses and embeddings" in {
    // Test the general case, with inverses and embeddings.  Note that we should always include
    // the relation itself, even if there are embeddings.  Sometimes we use both the original
    // edge and the embedding as edges in the graph.  If the original edge is still there, this
    // will stop it, and if it's not there, having it in this list won't hurt anything.
    val params = ("inverses" -> inversesFile) ~
      ("embeddings" -> embeddingsFile) ~
      ("use embeddings" -> true)
    val unallowedEdges = relationMetadataWithParams(params).getUnallowedEdges(relation, graph)
    println(unallowedEdges)
    unallowedEdges.size should be(6)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded1), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded2), 2)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded3), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(inverse), 1)
  }

  it should "work with no inverses" in {
    val params = ("embeddings" -> embeddingsFile) ~ ("use embeddings" -> true)
    val unallowedEdges = relationMetadataWithParams(params).getUnallowedEdges(relation, graph)
    unallowedEdges.size should be(3)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded1), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded2), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
  }

  it should "work with no embeddings" in {
    val params = ("inverses" -> inversesFile)
    val unallowedEdges = relationMetadataWithParams(params).getUnallowedEdges(relation, graph)
    unallowedEdges.size should be(2)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(inverse), 1)
  }

  it should "work when the relation has no inverse" in {
    val params = ("inverses" -> inversesFile)
    val unallowedEdges = relationMetadataWithParams(params).getUnallowedEdges(relation2, graph)
    unallowedEdges.size should be(1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation2), 1)
  }

  it should "work when the inverse has no embeddings" in {
    // This test is due to a bug.
    val params = ("inverses" -> inversesFile) ~
      ("embeddings" -> embeddingsFile2) ~
      ("use embeddings" -> true)
    val unallowedEdges = relationMetadataWithParams(params).getUnallowedEdges(relation, graph)
    unallowedEdges.size should be(4)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded1), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(embedded2), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(relation), 1)
    expectCount(unallowedEdges, edgeDict.getIndex(inverse), 1)
  }
}
