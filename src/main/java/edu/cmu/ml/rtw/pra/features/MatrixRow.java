package edu.cmu.ml.rtw.pra.features;

import java.util.Arrays;

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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + columns;
    result = prime * result + Arrays.hashCode(pathTypes);
    result = prime * result + sourceNode;
    result = prime * result + targetNode;
    result = prime * result + Arrays.hashCode(values);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    MatrixRow other = (MatrixRow) obj;
    if (columns != other.columns)
      return false;
    if (!Arrays.equals(pathTypes, other.pathTypes))
      return false;
    if (sourceNode != other.sourceNode)
      return false;
    if (targetNode != other.targetNode)
      return false;
    if (!Arrays.equals(values, other.values))
      return false;
    return true;
  }
}
