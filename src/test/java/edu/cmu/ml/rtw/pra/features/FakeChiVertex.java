package edu.cmu.ml.rtw.pra.features;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.graphchi.ChiVertex;
import edu.cmu.graphchi.EmptyType;

public class FakeChiVertex extends ChiVertex<EmptyType, Integer> {
    private int vertexId;
    private List<FakeChiEdge> outEdges;
    private List<FakeChiEdge> inEdges;

    public FakeChiVertex(int vertexId) {
        super(vertexId, null);
        this.vertexId = vertexId;
        outEdges = new ArrayList<FakeChiEdge>();
        inEdges = new ArrayList<FakeChiEdge>();
    }

    public void setVertexId(int vertexId) {
        this.vertexId = vertexId;
    }

    public void addInEdge(int vertex, int edgeType) {
        inEdges.add(new FakeChiEdge(vertex, edgeType));
    }

    public void addOutEdge(int vertex, int edgeType) {
        outEdges.add(new FakeChiEdge(vertex, edgeType));
    }

    @Override
    public int getId() {
        return vertexId;
    }

    @Override
    public int numInEdges() {
        return inEdges.size();
    }

    @Override
    public int numOutEdges() {
        return outEdges.size();
    }

    @Override
    public int numEdges() {
        return numInEdges() + numOutEdges();
    }

    @Override
    public int getOutEdgeId(int num) {
      return outEdges.get(num).getVertexId();
    }

    @Override
    public FakeChiEdge outEdge(int num) {
        return outEdges.get(num);
    }

    @Override
    public FakeChiEdge inEdge(int num) {
        return inEdges.get(num);
    }
}
