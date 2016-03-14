package edu.cmu.ml.rtw.pra.experiments

import java.io.File

import scala.collection.mutable

import com.mattg.util.FileUtil

trait MetricComputer {
  def computeDatasetMetrics(results_dir: String, split_dir: String, relation_metrics: RelationMetrics): MutableMetrics
  def computeRelationMetrics(results_file: String, test_split_file: String): MutableMetrics
  def datasetMetricsComputed: Seq[String]
  def relationMetricsComputed: Seq[String]

  // TODO(matt): If I ever get around to testing this stuff, this needs to be injected from
  // somewhere instead of just instantiated right here.
  val fileUtil = new FileUtil()

  def readPredictionsFromFile(results_file: String): List[Prediction] = {
    val predictions = new mutable.ListBuffer[Prediction]
    for (line <- fileUtil.getLineIterator(results_file)) {
      val fields = line.split("\t")
      if (fields.length > 2 && fields(1).nonEmpty) {
        val score = fields(2).toDouble
        if (score > 0) {
          val arg1 = fields(0)
          val arg2 = fields(1)
          val correct = if (fields.length < 4) false else fields(3).contains("*")
          if (fields.length < 4 || !fields(3).contains("*^")) {
            predictions += Tuple4(score, correct, arg1, arg2)
          }
        }
      }
    }
    predictions.toList.sortBy(-_._1)
  }

  def readPositiveInstancesFromDataFile(data_file: String): Map[String, Set[String]] = {
    val instances = new mutable.HashMap[String, mutable.Set[String]]().withDefaultValue(new mutable.HashSet[String])
    if (new File(data_file).exists) {
      for (line <- fileUtil.getLineIterator(data_file)) {
        val fields = line.split("\t")
        if (fields.length == 2 || fields(2) == "1") {
          instances.update(fields(0), instances(fields(0)) + fields(1))
        }
      }
    }
    instances.map(x => (x._1, x._2.toSet)).toMap
  }
}

object BasicMetricComputer extends MetricComputer {
  def computeDatasetMetrics(results_dir: String, split_dir: String, relation_metrics: RelationMetrics) = {
    val metrics = EmptyMetricsWithDefault
    metrics("MAP") = relation_metrics.map(_._2("AP")).sum / relation_metrics.keys.size
    metrics("MRR") = relation_metrics.map(_._2("RR")).sum / relation_metrics.keys.size
    metrics("MMAP") = relation_metrics.map(_._2("Per Query MAP")).sum / relation_metrics.keys.size
    metrics("MMRR") = relation_metrics.map(_._2("Per Query MRR")).sum / relation_metrics.keys.size
    metrics("MMAP Predicted") = average(relation_metrics.map(_._2("Predicted MAP")).filter(_ > -1))
    metrics("MMRR Predicted") = average(relation_metrics.map(_._2("Predicted MRR")).filter(_ > -1))
    metrics
  }

  def computeRelationMetrics(results_file: String, test_split_file: String) = {
    val metrics = EmptyMetricsWithDefault
    val predictions = readPredictionsFromFile(results_file)
    val testData = readPositiveInstancesFromDataFile(test_split_file).withDefaultValue(Set())
    val testInstanceSet = new mutable.HashSet[(String, String)]
    for (source <- testData.keys;
         target <- testData(source)) {
      testInstanceSet.add((source, target))
    }
    val ap_and_rr = computeApAndRr(predictions, testInstanceSet.toSet)
    metrics("AP") = ap_and_rr._1
    metrics("RR") = ap_and_rr._2

    val map_results = new mutable.ListBuffer[Double]
    val mrr_results = new mutable.ListBuffer[Double]
    val preds_only_map_results = new mutable.ListBuffer[Double]
    val preds_only_mrr_results = new mutable.ListBuffer[Double]
    for (source <- testData.keys) {
      val source_preds = predictions.filter(_._3 == source)
      val source_instances = testData(source).map((source, _)).toSet
      val map_and_mrr = computeApAndRr(source_preds, source_instances)
      map_results += map_and_mrr._1
      mrr_results += map_and_mrr._2
      if (source_preds.size > 0) {
        preds_only_map_results += map_and_mrr._1
        preds_only_mrr_results += map_and_mrr._2
      }
    }
    metrics("Per Query MAP") = average(map_results)
    metrics("Per Query MRR") = average(mrr_results)
    metrics("Predicted MAP") = average(preds_only_map_results)
    metrics("Predicted MRR") = average(preds_only_mrr_results)
    val num_no_preds = (testData.keys.toSet -- predictions.map(_._3).toSet).size
    metrics("% no predictions") = num_no_preds.toDouble / testData.keys.size
    metrics
  }

  def average(list: Iterable[Double]) = if ((list.size) == 0) -1 else list.sum / list.size

  def computeApAndRr(predictions: Seq[Prediction], instances: Set[(String, String)]) = {
    var total_predictions = 0
    var total_precision = 0.0
    var total_correct = 0.0
    var first_correct = -1
    for (prediction <- predictions) {
      total_predictions += 1
      if (instances.contains((prediction._3, prediction._4))) {
        total_correct += 1.0
        if (first_correct == -1) first_correct = total_predictions;
        total_precision += (total_correct / total_predictions)
      }
    }
    val reciprocal_rank = if (first_correct == -1) 0 else 1.0 / first_correct
    val average_precision = if (instances.size == 0) 0 else total_precision / instances.size
    (average_precision, reciprocal_rank)
  }

  def datasetMetricsComputed = {
    Seq(
      "MAP",
      "MRR",
      "MMAP",
      "MMRR",
      "MMAP Predicted",
      "MMRR Predicted"
    )
  }

  def relationMetricsComputed = {
    Seq(
      "AP",
      "RR",
      "Per Query MAP",
      "Per Query MRR",
      "Predicted MAP",
      "Predicted MRR",
      "% no predictions"
    )
  }
}
