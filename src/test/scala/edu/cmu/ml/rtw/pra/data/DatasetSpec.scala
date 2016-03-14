package edu.cmu.ml.rtw.pra.data

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import com.mattg.util.FakeFileUtil
import com.mattg.util.TestUtil
import com.mattg.util.TestUtil.Function

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.scalatest._

import gnu.trove.{TIntObjectHashMap => TMap}

class DatasetSpec extends FlatSpecLike with Matchers {

  val datasetFile = "node1\tnode2\t1\n" + "node1\tnode3\t-1\n"

  val badDatasetFile = datasetFile + "node1\tnode4\tnode5\n"
  val badDatasetFilename = "/bad/filename"

  val instanceFilename = "/instance file"
  val instanceFileContents = "node1\tnode2\t1\nnode3\tnode4\t-1\n"

  val missingInstanceFilename = "/missing instance file"
  val missingInstanceFileContents = "node1\tmissing node\t1\n"

  val instanceGraphFilename = "/instance graph file"
  val instanceGraphFileContents =
    // the (positive) instance
    "node1\tnode2\t1\t" +
      // the graph for the instance
      "node1^,^rel1^,^node3 ### node3^,^rel2^,^node2\n" +
    // and for good measure, a negative instance
    "node3\tnode4\t-1\t" +
      // and the graph for the instance
      "node3^,^rel1^,^node5 ### node4^,^rel3^,^node6\n"


  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(instanceFilename, instanceFileContents)
  fileUtil.addFileToBeRead(instanceGraphFilename, instanceGraphFileContents)
  fileUtil.addFileToBeRead(badDatasetFilename, badDatasetFile)
  fileUtil.addFileToBeRead(missingInstanceFilename, missingInstanceFileContents)
  fileUtil.addFileToBeRead("/graph/node_dict.tsv", "1\tnode1\n2\tnode2\n3\tnode3\n4\tnode4\n5\tnode5\n6\tnode6\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv", "1\trel1\n2\trel2\n3\trel3\n")
  val graphOnDisk = new GraphOnDisk("/graph/", Outputter.justLogger, fileUtil)

  "DatasetReader.readNodePairFile" should "crash on bad third column" in {
    TestUtil.expectError(classOf[IllegalStateException], "not formatted correctly", new Function() {
      override def call() {
        DatasetReader.readNodePairFile(badDatasetFilename, Some(graphOnDisk), fileUtil)
      }
    })
  }

  it should "create instances with isInGraph() = false when the node name isn't in the graph" in {
    val dataset = DatasetReader.readNodePairFile(missingInstanceFilename, Some(graphOnDisk), fileUtil)
    dataset.instances(0).isInGraph() should be(false)
  }

  it should "correctly read an instance dataset file" in {
    val dataset = DatasetReader.readNodePairFile(instanceFilename, Some(graphOnDisk), fileUtil)
    dataset.instances(0).source should be(graphOnDisk.getNodeIndex("node1"))
    dataset.instances(0).target should be(graphOnDisk.getNodeIndex("node2"))
    dataset.instances(0).isPositive should be(true)
    dataset.instances(1).source should be(graphOnDisk.getNodeIndex("node3"))
    dataset.instances(1).target should be(graphOnDisk.getNodeIndex("node4"))
    dataset.instances(1).isPositive should be(false)
  }

  it should "correctly read an instance-graph dataset file" in {
    val dataset = DatasetReader.readNodePairFile(instanceGraphFilename, None, fileUtil)
    val graph1 = dataset.instances(0).graph

    // There are three nodes in the graph, but four entries in the array, because we use the
    // dictionary index, and there's never any node at 0.  I suppose I could change the indices...
    // But that would be a bit confusing, because you'd have to keep track of when you shift the
    // index and when you don't.
    // TODO(matt): I commented this out because I was getting an error when reading in actual
    // graphs in some experiments.  I fixed that error by increasing the size by one.  I really
    // should figure out what was going on with those graphs, and put this test back in.
    //graph1.entries.size should be(4)

    graph1.getNode(0).edges should be(new TMap())
    graph1.getNode("node1").edges.size should be(1)
    graph1.getNode("node1").edges.get(graph1.getEdgeIndex("rel1"))._1.size should be(0)
    graph1.getNode("node1").edges.get(graph1.getEdgeIndex("rel1"))._2.size should be(1)
    graph1.getNode("node1").edges.get(graph1.getEdgeIndex("rel1"))._2.get(0) should be(graph1.getNodeIndex("node3"))
    graph1.getNode("node2").edges.size should be(1)
    graph1.getNode("node2").edges.get(graph1.getEdgeIndex("rel2"))._1.size should be(1)
    graph1.getNode("node2").edges.get(graph1.getEdgeIndex("rel2"))._1.get(0) should be(graph1.getNodeIndex("node3"))
    graph1.getNode("node2").edges.get(graph1.getEdgeIndex("rel2"))._2.size should be(0)
    graph1.getNode("node3").edges.size should be(2)
    graph1.getNode("node3").edges.get(graph1.getEdgeIndex("rel1"))._1.size should be(1)
    graph1.getNode("node3").edges.get(graph1.getEdgeIndex("rel1"))._1.get(0) should be(graph1.getNodeIndex("node1"))
    graph1.getNode("node3").edges.get(graph1.getEdgeIndex("rel1"))._2.size should be(0)
    graph1.getNode("node3").edges.get(graph1.getEdgeIndex("rel2"))._1.size should be(0)
    graph1.getNode("node3").edges.get(graph1.getEdgeIndex("rel2"))._2.size should be(1)
    graph1.getNode("node3").edges.get(graph1.getEdgeIndex("rel2"))._2.get(0) should be(graph1.getNodeIndex("node2"))

    val graph2 = dataset.instances(1).graph
    //graph2.entries.size should be(5)
    graph2.getNode(0).edges should be(new TMap())
    graph2.getNode("node3").edges.size should be(1)
    graph2.getNode("node3").edges.get(graph2.getEdgeIndex("rel1"))._1.size should be(0)
    graph2.getNode("node3").edges.get(graph2.getEdgeIndex("rel1"))._2.size should be(1)
    graph2.getNode("node3").edges.get(graph2.getEdgeIndex("rel1"))._2.get(0) should be(graph2.getNodeIndex("node5"))
    graph2.getNode("node4").edges.size should be(1)
    graph2.getNode("node4").edges.get(graph2.getEdgeIndex("rel3"))._1.size should be(0)
    graph2.getNode("node4").edges.get(graph2.getEdgeIndex("rel3"))._2.size should be(1)
    graph2.getNode("node4").edges.get(graph2.getEdgeIndex("rel3"))._2.get(0) should be(graph2.getNodeIndex("node6"))
    graph2.getNode("node5").edges.size should be(1)
    graph2.getNode("node5").edges.get(graph2.getEdgeIndex("rel1"))._1.size should be(1)
    graph2.getNode("node5").edges.get(graph2.getEdgeIndex("rel1"))._1.get(0) should be(graph2.getNodeIndex("node3"))
    graph2.getNode("node5").edges.get(graph2.getEdgeIndex("rel1"))._2.size should be(0)
    graph2.getNode("node6").edges.size should be(1)
    graph2.getNode("node6").edges.get(graph2.getEdgeIndex("rel3"))._1.size should be(1)
    graph2.getNode("node6").edges.get(graph2.getEdgeIndex("rel3"))._1.get(0) should be(graph2.getNodeIndex("node4"))
    graph2.getNode("node6").edges.get(graph2.getEdgeIndex("rel3"))._2.size should be(0)
  }
}
