package edu.cmu.ml.rtw.pra;

public class PathType {
    final public int[] edgeTypes;
    final public boolean[] reverse;
    final public int numHops;
    final public String description;

    public PathType(String description) {
        this.description = description;
        // Description is formatted like -1-2-3-4-; doing split("-") would result in an empty
        // string as the first element, so we call substring(1) first.
        String[] parts = description.substring(1).split("-");
        numHops = parts.length;
        edgeTypes = new int[numHops];
        reverse = new boolean[numHops];
        for (int i=0; i<parts.length; i++) {
            if (parts[i].charAt(0) == '_') {
                reverse[i] = true;
                parts[i] = parts[i].substring(1);
            }
            edgeTypes[i] = Integer.parseInt(parts[i]);
        }
    }

    private PathType() {
        edgeTypes = null;
        reverse = null;
        numHops = 0;
        description = "null";
    }

    public static final PathType NULL = new PathType();
}
