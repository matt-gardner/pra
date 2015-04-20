package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

object generate_tuning_specs {

  def fillTemplate(params: (String, Int, Double, Double)): String = {
    s"""load new_feature_experiment_base
    |load default_subgraph_parameters
    |{
    |  "pra parameters": {
    |    "features": {
    |      "feature extractors": [
    |      ${params._1}
    |      ],
    |      "feature size": ${params._2}
    |    }
    |    "learning": {
    |      "l1 weight": ${params._3},
    |      "l2 weight": ${params._4}
    |    }
    |  }
    |}""".stripMargin
  }

  def main(args: Array[String]) {
    val fileUtil = new FileUtil
    val base = "/home/mg1/pra/experiment_specs/nell/new_features/tuning/"
    fileUtil.mkdirs(base)
    val extractors = Seq(
      //("\"PraFeatureExtractor\"", "pra_")
      ("\"PraFeatureExtractor\", \"OneSidedFeatureExtractor\"", "pra_one_sided_"),
      ("\"PraFeatureExtractor\", \"OneSidedFeatureExtractor\", \"CategoricalComparisonFeatureExtractor\"", "pra_one_sided_catcomp_")
    )

    val featureSizes = Seq(-1, 10000, 100000)
    val l1Values = Seq(0.005, 0.05, 0.5, 5)
    val l2Values = Seq(0.01, 0.1, 1)

    for (extractor <- extractors;
         featureSize <- featureSizes;
         l1Value <- l1Values;
         l2Value <- l2Values) {
      val contents = fillTemplate((extractor._1, featureSize, l1Value, l2Value))
      val filename = if (featureSize == -1) {
        s"${extractor._2}l1-${l1Value}_l2-${l2Value}.json"
      } else {
        s"${extractor._2}l1-${l1Value}_l2-${l2Value}_f-${featureSize}.json"
      }
      val writer = fileUtil.getFileWriter(base + filename)
      writer.write(contents)
      writer.close()
    }
  }
}
