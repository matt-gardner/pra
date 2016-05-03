package edu.cmu.ml.rtw.pra.data

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.PprNegativeExampleSelector
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.Dictionary
import com.mattg.util.FakeFileUtil
import com.mattg.util.Pair
import com.mattg.util.TestUtil
import com.mattg.util.TestUtil.Function

import scala.collection.mutable
import scala.collection.JavaConverters._

import java.io.BufferedReader
import java.io.StringReader

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.JsonMethods.{pretty,render}

class SplitCreatorSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger

  val params: JValue =
    ("percent training" -> .3) ~
    ("relations" -> Seq("rel/1")) ~
    ("relation metadata" -> "nell") ~
    ("graph" -> "nell")
  val praBase = "/"
  val splitDir = "/splits/split_name/"
  val dataFile = "node1\tnode2\n"

  val fakeFileUtil = new FakeFileUtil
  fakeFileUtil.addFileToBeRead("/graphs/nell/num_shards.tsv", "1\n")
  fakeFileUtil.addFileToBeRead("/relation_metadata/nell/category_instances/c1", "node1\n")
  fakeFileUtil.addFileToBeRead("/relation_metadata/nell/category_instances/c2", "node2\n")
  fakeFileUtil.addFileToBeRead("/relation_metadata/nell/domains.tsv", "rel/1\tc1\n")
  fakeFileUtil.addFileToBeRead("/relation_metadata/nell/ranges.tsv", "rel/1\tc2\n")
  fakeFileUtil.addFileToBeRead("/relation_metadata/nell/relations/rel_1", "node1\tnode2\n")
  fakeFileUtil.addFileToBeRead("/graphs/nell/node_dict.tsv", "1\tnode1\n2\tnode2\n")
  fakeFileUtil.addFileToBeRead("/graphs/nell/edge_dict.tsv", "1\trel/1\n")
  fakeFileUtil.onlyAllowExpectedFiles()
  val splitCreator = new SplitCreator(params, praBase, splitDir, outputter, fakeFileUtil)
  val graph = new GraphOnDisk("/graphs/nell/", outputter, fakeFileUtil)

  val positiveInstances = Seq(new NodePairInstance(1, 1, true, graph), new NodePairInstance(1, 2, true, graph))
  val negativeInstances = Seq(new NodePairInstance(2, 2, false, graph), new NodePairInstance(1, 2, false, graph))
  val goodData = new Dataset[NodePairInstance](positiveInstances ++ negativeInstances) {
    override def splitData(percent: Double) = {
      println("Splitting fake data")
      val training = new Dataset[NodePairInstance](positiveInstances.take(1) ++ negativeInstances.take(1))
      val testing = new Dataset[NodePairInstance](positiveInstances.drop(1) ++ negativeInstances.drop(1))
      (training, testing)
    }
  }
  val badData = new Dataset[NodePairInstance](Seq())

  "createNegativeExampleSelector" should "return null with no input" in {
    splitCreator.createNegativeExampleSelector(JNothing) should be(null)
  }

  it should "return a PprNegativeExampleSelector with the right input" in {
    val params: JValue = ("ppr computer" -> ("iterations" -> 1) ~ ("type" -> "GraphChiPprComputer"))
    val selector = splitCreator.createNegativeExampleSelector(params)
    val graph = selector.graph.asInstanceOf[GraphOnDisk]
    graph.graphFile should be("/graphs/nell/graph_chi/edges.tsv")
    graph.numShards should be(1)
  }

  "addNegativeExampels" should "read domains and ranges correctly" in {
    val relation = "rel1"
    val domains = Map(relation -> "c1")
    val ranges = Map(relation -> "c2")
    var creator = splitCreatorWithFakeNegativeSelector(Some(Set(1)), Some(Set(2)))
    creator.addNegativeExamples(goodData, Seq(), relation, domains, ranges, graph.nodeDict) should be(goodData)
    // Adding a test with the wrong sources and targets, just to be sure the test is really // working.
    creator = splitCreatorWithFakeNegativeSelector(Some(Set(2)), Some(Set(1)))
    creator.addNegativeExamples(goodData, Seq(), relation, domains, ranges, graph.nodeDict) should be(badData)
  }

  it should "handle null domains and ranges" in {
    val creator = splitCreatorWithFakeNegativeSelector(None, None)
    creator.addNegativeExamples(goodData, Seq(), "rel1", null, null, graph.nodeDict) should be(goodData)
  }

  it should "throw an error if the relation is missing from domain or range" in {
    val creator = splitCreatorWithFakeNegativeSelector(None, None)
    TestUtil.expectError(classOf[NoSuchElementException], new Function() {
      def call() {
        creator.addNegativeExamples(goodData, Seq(), "rel1", Map(), null, graph.nodeDict) should be(goodData)
      }
    })
    TestUtil.expectError(classOf[NoSuchElementException], new Function() {
      def call() {
        creator.addNegativeExamples(goodData, Seq(), "rel1", null, Map(), graph.nodeDict) should be(goodData)
      }
    })
  }

  "createSplit" should "correctly create a split" in {
    // TODO(matt): if these tests are run out of order, or another one is added after this, this
    // could easily break.  The fileUtil needs to be reset.
    fakeFileUtil.addExpectedFileWritten("/splits/split_name/in_progress", "")
    fakeFileUtil.addExpectedFileWritten("/splits/split_name/params.json", pretty(render(params)))
    fakeFileUtil.addExpectedFileWritten("/splits/split_name/relations_to_run.tsv", "rel/1\n")
    val trainingFile = "node1\tnode1\t1\nnode2\tnode2\t-1\n"
    fakeFileUtil.addExpectedFileWritten("/splits/split_name/rel_1/training.tsv", trainingFile)
    val testingFile = "node1\tnode2\t1\nnode1\tnode2\t-1\n"
    fakeFileUtil.addExpectedFileWritten("/splits/split_name/rel_1/testing.tsv", testingFile)
    var creator = splitCreatorWithFakeNegativeSelector(Some(Set(1)), Some(Set(2)))
    creator.createSplit()
    fakeFileUtil.expectFilesWritten()
  }


  def splitCreatorWithFakeNegativeSelector(expectedSources: Option[Set[Int]], expectedTargets: Option[Set[Int]]) = {
    new SplitCreator(params, praBase, splitDir, outputter, fakeFileUtil) {
      override def createNegativeExampleSelector(params: JValue) = {
        new FakeNegativeExampleSelector(expectedSources, expectedTargets)
      }
    }
  }

  class FakeNegativeExampleSelector(expectedSources: Option[Set[Int]], expectedTargets: Option[Set[Int]])
      extends PprNegativeExampleSelector(JNothing, new GraphOnDisk("src/test/resources/", outputter), outputter) {
    override def selectNegativeExamples(
        data: Dataset[NodePairInstance],
        otherPositives: Seq[NodePairInstance],
        allowedSources: Option[Set[Int]],
        allowedTargets: Option[Set[Int]]): Dataset[NodePairInstance] = {
      if (expectedSources == allowedSources && expectedTargets == allowedTargets) {
        goodData
      } else {
        badData
      }
    }
  }
}
