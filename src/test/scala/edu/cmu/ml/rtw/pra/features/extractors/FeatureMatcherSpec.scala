package edu.cmu.ml.rtw.pra.features.extractors

import edu.cmu.ml.rtw.pra.experiments.Outputter
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

  "PraFeatureMatcher" should "match specific path types" in {
    val feature = "-rel1-rel2-"
    val matcher = new PraFeatureExtractor().getFeatureMatcher(feature, graph).get
    matcher.isFinished(0) should be(false)
    matcher.isFinished(1) should be(false)
    matcher.isFinished(2) should be(true)

    matcher.edgeOk(0, 0) should be(false)
    matcher.edgeOk(1, 0) should be(true)
    matcher.edgeOk(2, 0) should be(false)
    matcher.edgeOk(3, 0) should be(false)
    matcher.edgeOk(4, 0) should be(false)
    matcher.edgeOk(0, 1) should be(false)
    matcher.edgeOk(1, 1) should be(false)
    matcher.edgeOk(2, 1) should be(true)
    matcher.edgeOk(3, 1) should be(false)
    matcher.edgeOk(4, 1) should be(false)
    matcher.edgeOk(0, 2) should be(false)
    matcher.edgeOk(1, 2) should be(false)
    matcher.edgeOk(2, 2) should be(false)
    matcher.edgeOk(3, 2) should be(false)
    matcher.edgeOk(4, 2) should be(false)

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
  }
}
