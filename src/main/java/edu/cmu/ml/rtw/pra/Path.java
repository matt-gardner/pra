package edu.cmu.ml.rtw.pra;

public class Path {
    private int[] nodes;
    private int[] edges;
    private boolean[] reverse;
    private int startNode;
    private int hops;

    public Path(int startNode, int maxHops) {
        this.startNode = startNode;
        hops = 0;
        nodes = new int[maxHops];
        edges = new int[maxHops];
        reverse = new boolean[maxHops];
    }

    public void addHop(int node, int edge, boolean rev) {
        nodes[hops] = node;
        edges[hops] = edge;
        reverse[hops] = rev;
        hops++;
    }

    public boolean alreadyVisited(int node) {
        // We could try to be smart with some kind of binary search, or something, but while
        // MAX_HOPS is around 10 (and generally the number of actual hops is 5 or less), it
        // seems like this should be fast enough.
        if (node == startNode) {
            return true;
        }
        for (int i=0; i<hops; i++) {
            if (nodes[i] == node)
                return true;
        }
        return false;
    }

    public int[] getNodes() {
        return nodes;
    }

    public int[] getEdges() {
        return edges;
    }

    public boolean[] getReverse() {
        return reverse;
    }

    public int getStartNode() {
        return startNode;
    }

    public int getHops() {
        return hops;
    }
}
