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
  }
}
