package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

object generate_tuning_specs {

  val fileUtil = new FileUtil

  def fillBfsTemplate(params: (String, Int, Double, Double)): String = {
    s"""load new_feature_experiment_base
    |{
    |  "split": "nell_with_negatives",
    |  "pra parameters": {
    |    "features": {
    |      "type": "subgraphs",
    |      "path finder": {
    |        "type": "BfsPathFinder",
    |        "number of steps": 2
    |      },
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

  def fillStandardTemplate(params: (String, Double, Double)): String = {
    s"""load new_feature_experiment_base
    |{
    |  "split": "nell_with_negatives",
    |  "pra parameters": {
    |    "mode": "learn models",
    |    "features": {
    |      "path finder": {
    |        ${params._1}
    |        "walks per source": 100,
    |        "path finding iterations": 3,
    |        "path accept policy": "paired-only"
    |      },
    |      "path selector": {
    |        "number of paths to keep": 1000
    |      },
    |      "path follower": {
    |        "walks per path": 50,
    |        "matrix accept policy": "paired-targets-only"
    |      }
    |    }
    |    "learning": {
    |      "l1 weight": ${params._2},
    |      "l2 weight": ${params._3}
    |    }
    |  }
    |}""".stripMargin
  }

  def createBfsTuningSpecs() {
    val base = "/home/mg1/pra/experiment_specs/nell/new_features/bfs/tuning/"
    fileUtil.mkdirs(base)
    val pra = "\"PraFeatureExtractor\""
    val bigrams = "\"PathBigramsFeatureExtractor\""
    val anyrel = "\"AnyRelFeatureExtractor\""
    val one_sided = "\"OneSidedFeatureExtractor\""
    val catcomp = "\"CategoricalComparisonFeatureExtractor\""
    val matrix_name = "my_svd/nell/kb-t_svo/similarity_matrix_0.8_3_20_max_10"
    val vecsim = "{\"name\": \"VectorSimilarityFeatureExtractor\", \"matrix name\": \"" + matrix_name + "\"}"
    val extractors = Seq(
      //(pra, "pra_"),
      //(Seq(pra, bigrams).mkString(", "), "pra_bigrams_"),
      //(Seq(pra, one_sided).mkString(", "), "pra_one_sided_"),
      //(Seq(pra, catcomp).mkString(", "), "pra_catcomp_"),
      //(Seq(pra, one_sided, catcomp).mkString(", "), "pra_one_sided_catcomp_"),
      //(Seq(pra, vecsim).mkString(", "), "pra_vs_"),
      //(Seq(pra, bigrams, vecsim).mkString(", "), "pra_bigrams_vs_")
      (Seq(pra, anyrel).mkString(", "), "pra_anyrel_")
    )

    val featureSizes = Seq(-1)
    val l1Values = Seq(0.05, 0.5)
    val l2Values = Seq(0.01, 0.1, 0.5)

    for (extractor <- extractors;
         featureSize <- featureSizes;
         l1Value <- l1Values;
         l2Value <- l2Values) {
      val contents = fillBfsTemplate((extractor._1, featureSize, l1Value, l2Value))
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

  def createStandardTuningSpecs() {
    val base = "/home/mg1/pra/experiment_specs/nell/new_features/standard/tuning/"
    fileUtil.mkdirs(base)
    val vector_params = Seq("",
      """"path type factory": {
        |  "name": "VectorPathTypeFactory",
        |  "spikiness": 3,
        |  "reset weight": 0.25,
        |  "embeddings": "pca_svo"
        |},
        |""".stripMargin)
    val l1Values = Seq(0.005, 0.05, 0.5)
    val l2Values = Seq(0.001, 0.01, 0.1, 1.0)

    for (v_params <- vector_params;
         l1Value <- l1Values;
         l2Value <- l2Values) {
      val contents = fillStandardTemplate((v_params, l1Value, l2Value))
      val filename = if (v_params == "") {
        s"baseline_l1-${l1Value}_l2-${l2Value}.json"
      } else {
        s"baseline_vs_l1-${l1Value}_l2-${l2Value}.json"
      }
      val writer = fileUtil.getFileWriter(base + filename)
      writer.write(contents)
      writer.close()
    }
  }

  def main(args: Array[String]) {
    createStandardTuningSpecs()
  }
}
