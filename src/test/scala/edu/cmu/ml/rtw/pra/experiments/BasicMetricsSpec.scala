package edu.cmu.ml.rtw.pra.experiments

import org.scalatest._

class BasicMetricsSpec extends FlatSpecLike with Matchers {

  val predictions = Seq(
    (1.0, false, "1", "2"),
    (0.9, true, "3", "4"),
    (0.8, false, "5", "6"),
    (0.7, true, "7", "8")
    )
  val instances = Set(
    ("3", "4"),
    ("7", "8")
    )

  "computeApAndPr" should "compute AP and RR correctly in a simple data set" in {
    BasicMetricComputer.computeApAndRr(predictions, instances) should be(.5, .5)
  }

  it should "ignore the true/false in predictions and just use instances" in {
    BasicMetricComputer.computeApAndRr(predictions, Set(("7", "8"))) should be(.25, .25)
  }

  it should "have the correct denominator with missing instances" in {
    BasicMetricComputer.computeApAndRr(predictions, instances ++ Set(("1", "1"), ("2", "2"))) should be(.25, .5)
  }
}
