package edu.cmu.ml.rtw.pra

import scala.collection.mutable

package object experiments {
  type Metrics = Map[String, Double]
  type MutableMetrics = mutable.Map[String, Double]
  type RelationMetrics = Map[String, Metrics]
  type MutableRelationMetrics = mutable.Map[String, MutableMetrics]
  type ExperimentMetrics = Map[String, RelationMetrics]
  type MutableExperimentMetrics = mutable.Map[String, MutableRelationMetrics]
  type Prediction = (Double, Boolean, String, String)

  def makeMetricsImmutable(metrics: MutableMetrics): Metrics = metrics.toMap
  def makeRelationMetricsImmutable(metrics: MutableRelationMetrics): RelationMetrics = {
    metrics.map(x => (x._1, makeMetricsImmutable(x._2))).toMap
  }
  def makeExperimentMetricsImmutable(metrics: MutableExperimentMetrics): ExperimentMetrics = {
    metrics.map(x => (x._1, makeRelationMetricsImmutable(x._2))).toMap
  }

  def EmptyMetricsWithDefault: MutableMetrics = {
    new mutable.HashMap[String, Double].withDefaultValue(-1.0)
  }

  def EmptyRelationMetricsWithDefaults: MutableRelationMetrics = {
    new mutable.HashMap[String, MutableMetrics].withDefaultValue(EmptyMetricsWithDefault)
  }

  def EmptyExperimentMetricsWithDefaults: MutableExperimentMetrics = {
    new mutable.HashMap[String, MutableRelationMetrics].withDefaultValue(EmptyRelationMetricsWithDefaults)
  }
}
