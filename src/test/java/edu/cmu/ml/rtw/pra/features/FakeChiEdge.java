package edu.cmu.ml.rtw.pra.features;

import edu.cmu.graphchi.ChiEdge;

public class FakeChiEdge implements ChiEdge<Integer> {
    private int edgeType;
    private int targetVertex;

    public FakeChiEdge(int targetVertex, int edgeType) {
        this.edgeType = edgeType;
        this.targetVertex = targetVertex;
    }

    @Override
    public Integer getValue() {
        return edgeType;
    }

    @Override
    public int getVertexId() {
        return targetVertex;
    }

    @Override
    public void setValue(Integer arg0) {
    }
}
