package edu.cmu.ml.rtw.pra.features;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;

public interface EdgeExcluder {
  public void prepUnallowedWalks(VertexIdTranslate vertexIdTranslate);
  public boolean shouldExcludeEdge(int sourceVertex,
                                   int nextVertex,
                                   Vertex atVertex,
                                   int edgeType);
}
