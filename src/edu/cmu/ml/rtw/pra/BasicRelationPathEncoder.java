package edu.cmu.ml.rtw.pra;

/**
 * A PathEncoder that returns a single path type per walk: the concatenation of all of the edge
 * types in the walk.
 */
public class BasicRelationPathEncoder implements PathEncoder {

    @Override
    public int getNumPaths() {
        return 1;
    }

    @Override
    public String[] encode(Path path) {
        StringBuilder builder = new StringBuilder();
        builder.append("-");
        boolean[] reverse = path.getReverse();
        int[] edges = path.getEdges();
        for (int i=0; i<path.getHops(); i++) {
            if (reverse[i]) {
                builder.append("_");
            }
            builder.append(edges[i]);
            builder.append("-");
        }
        String[] encoded = new String[1];
        encoded[0] = builder.toString();
        return encoded;
    }
}
