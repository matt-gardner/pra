package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.experiments.Outputter
import edu.cmu.ml.rtw.pra.features.BaseEdgeSequencePathType
import edu.cmu.ml.rtw.pra.features.BasicPathTypeFactory
import edu.cmu.ml.rtw.pra.graphs.GraphOnDisk
import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

import org.scalatest._

class FeatureMatcherSpec extends FlatSpecLike with Matchers {
  val outputter = Outputter.justLogger
  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead("/graph/node_dict.tsv",
    "1\tnode1\n2\tnode2\n3\tnode3\n4\tnode4\n5\t100\n6\t50\n")
  fileUtil.addFileToBeRead("/graph/edge_dict.tsv",
    "1\trel1\n2\trel2\n3\trel3\n4\trel4\n5\t@ALIAS@\n")
  val graph = new GraphOnDisk("/graph/", outputter, fileUtil)
  val factory = new BasicPathTypeFactory(graph)

  "PraFeatureExtractor.getFeatureMatcher" should "create a correct PraFeatureMatcher" in {
    val feature = "-rel1-rel2-"
    val pathType = factory.fromHumanReadableString(feature).asInstanceOf[BaseEdgeSequencePathType]
    val matcher = new PraFeatureExtractor().getFeatureMatcher(feature, true, graph).get
    // PraFeatureMatcher is a case class, and the pathType has a correct equals method, so this
    // should work.
    matcher should be(PraFeatureMatcher(pathType))

    // If we say that we're matching from the target instead of the source, that should have the
    // effect of reversing the path type used by the PraFeatureMatcher.
    val reverseFeature = "-_rel2-_rel1-"
    val reversePathType = factory.fromHumanReadableString(reverseFeature).asInstanceOf[BaseEdgeSequencePathType]

    // Note that we're giving this method the original feature, but with startFromSourceNode=false.
    val reverseMatcher = new PraFeatureExtractor().getFeatureMatcher(feature, false, graph).get
    reverseMatcher should be(PraFeatureMatcher(reversePathType))
  }

  it should "return None with various non-matching feature descriptions" in {
    val extractor = new PraFeatureExtractor()
    extractor.getFeatureMatcher("bad feature", true, graph) should be(None)
    extractor.getFeatureMatcher("SOURCE:-rel1-", true, graph) should be(None)
    extractor.getFeatureMatcher("TARGET:-rel2-", true, graph) should be(None)
    extractor.getFeatureMatcher("--", true, graph) should be(None)
    extractor.getFeatureMatcher("-rel1--", true, graph) should be(None)
    extractor.getFeatureMatcher("-rel1--rel2-", true, graph) should be(None)
    extractor.getFeatureMatcher("-", true, graph) should be(None)
    extractor.getFeatureMatcher("-incorrect-relation-names-", true, graph) should be(None)
    extractor.getFeatureMatcher("-rel1->node2-rel2->node3", true, graph) should be(None)
    extractor.getFeatureMatcher("-rel1->node2-rel2->", true, graph) should be(None)
    extractor.getFeatureMatcher("-@ANY_REL@-", true, graph) should be(None)
    extractor.getFeatureMatcher("ANYREL:-@ANY_REL@-", true, graph) should be(None)
  }

  "PraFeatureMatcher" should "match specific path types" in {
    val feature = "-rel1-rel2-"
    val pathType = factory.fromHumanReadableString(feature).asInstanceOf[BaseEdgeSequencePathType]
    val matcher = PraFeatureMatcher(pathType)
    matcher.isFinished(0) should be(false)
    matcher.isFinished(1) should be(false)
    matcher.isFinished(2) should be(true)

    matcher.edgeOk(0, true, 0) should be(false)
    matcher.edgeOk(0, false, 0) should be(false)
    matcher.edgeOk(1, true, 0) should be(false)
    matcher.edgeOk(1, false, 0) should be(true)
    matcher.edgeOk(2, true, 0) should be(false)
    matcher.edgeOk(2, false, 0) should be(false)
    matcher.edgeOk(3, true, 0) should be(false)
    matcher.edgeOk(3, false, 0) should be(false)
    matcher.edgeOk(4, true, 0) should be(false)
    matcher.edgeOk(4, false, 0) should be(false)


    matcher.edgeOk(0, true, 1) should be(false)
    matcher.edgeOk(0, false, 1) should be(false)
    matcher.edgeOk(1, true, 1) should be(false)
    matcher.edgeOk(1, false, 1) should be(false)
    matcher.edgeOk(2, true, 1) should be(false)
    matcher.edgeOk(2, false, 1) should be(true)
    matcher.edgeOk(3, true, 1) should be(false)
    matcher.edgeOk(3, false, 1) should be(false)
    matcher.edgeOk(4, true, 1) should be(false)
    matcher.edgeOk(4, false, 1) should be(false)

    matcher.edgeOk(0, true, 2) should be(false)
    matcher.edgeOk(0, false, 2) should be(false)
    matcher.edgeOk(1, true, 2) should be(false)
    matcher.edgeOk(1, false, 2) should be(false)
    matcher.edgeOk(2, true, 2) should be(false)
    matcher.edgeOk(2, false, 2) should be(false)
    matcher.edgeOk(3, true, 2) should be(false)
    matcher.edgeOk(3, false, 2) should be(false)
    matcher.edgeOk(4, true, 2) should be(false)
    matcher.edgeOk(4, false, 2) should be(false)

    matcher.nodeOk(0, 0) should be(true)
    matcher.nodeOk(1, 0) should be(true)
    matcher.nodeOk(2, 0) should be(true)
    matcher.nodeOk(3, 0) should be(true)
    matcher.nodeOk(4, 0) should be(true)
    matcher.nodeOk(0, 1) should be(true)
    matcher.nodeOk(1, 1) should be(true)
    matcher.nodeOk(2, 1) should be(true)
    matcher.nodeOk(3, 1) should be(true)
    matcher.nodeOk(4, 1) should be(true)
    matcher.nodeOk(0, 2) should be(true)
    matcher.nodeOk(1, 2) should be(true)
    matcher.nodeOk(2, 2) should be(true)
    matcher.nodeOk(3, 2) should be(true)
    matcher.nodeOk(4, 2) should be(true)

    matcher.allowedEdges(0) should be(Some(Set((1, false))))
    matcher.allowedEdges(1) should be(Some(Set((2, false))))
    matcher.allowedEdges(2) should be(None)
    matcher.allowedEdges(3) should be(None)

    matcher.allowedNodes(0) should be(None)
    matcher.allowedNodes(1) should be(None)
    matcher.allowedNodes(2) should be(None)
    matcher.allowedNodes(3) should be(None)
  }
}
