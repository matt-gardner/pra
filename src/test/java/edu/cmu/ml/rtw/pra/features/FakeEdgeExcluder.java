package edu.cmu.ml.rtw.pra.features;

import java.util.List;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;

public class FakeEdgeExcluder implements EdgeExcluder {

    private boolean shouldExclude;

    public FakeEdgeExcluder() {
        this.shouldExclude = false;
    }

    public void setShouldExclude(boolean shouldExclude) {
        this.shouldExclude = shouldExclude;
    }

    @Override
    public boolean shouldExcludeEdge(int sourceVertex, int atVertex,
            Vertex nextVertex, int edgeType) {
        return shouldExclude;
    }

    @Override
    public void prepUnallowedWalks(VertexIdTranslate vertexIdTranslate) {
    }
}
