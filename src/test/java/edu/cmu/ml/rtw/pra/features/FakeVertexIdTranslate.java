package edu.cmu.ml.rtw.pra.features;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;

public class FakeVertexIdTranslate extends VertexIdTranslate {

    public FakeVertexIdTranslate() {
        super();
    }

    @Override
    public int forward(int nodeId) {
        return nodeId;
    }
}
