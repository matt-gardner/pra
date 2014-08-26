package edu.cmu.ml.rtw.pra;

public class MatrixRow {
    final public int sourceNode;
    final public int targetNode;
    final public int[] pathTypes;
    final public double[] values;
    final public int columns;

    public MatrixRow(int sourceNode, int targetNode, int[] pathTypes, double[] values) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.pathTypes = pathTypes;
        this.values = values;
        columns = pathTypes.length;
    }
}
