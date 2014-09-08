package edu.cmu.ml.rtw.pra.on_demand;

public class PraPrediction implements Comparable<PraPrediction> {
  final public String relation;
  final public String sourceNode;
  final public String targetNode;
  final public String provenance;
  final public double score;

  public PraPrediction(String relation,
                       String sourceNode,
                       String targetNode,
                       String provenance,
                       double score) {
    this.relation = relation;
    this.sourceNode = sourceNode;
    this.targetNode = targetNode;
    this.provenance = provenance;
    this.score = score;
  }

  @Override
  public int compareTo(PraPrediction other) {
    return -Double.compare(score, other.score);
  }
}
