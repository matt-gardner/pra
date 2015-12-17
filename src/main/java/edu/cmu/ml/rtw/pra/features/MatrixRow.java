package edu.cmu.ml.rtw.pra.features;

import java.util.Arrays;

import edu.cmu.ml.rtw.pra.data.Instance;

public class MatrixRow {
  final public Instance instance;
  final public int[] featureTypes;
  final public double[] values;
  final public int columns;

  public MatrixRow(Instance instance, int[] featureTypes, double[] values) {
    this.instance = instance;
    this.featureTypes = featureTypes;
    this.values = values;
    columns = featureTypes.length;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + columns;
    result = prime * result + Arrays.hashCode(featureTypes);
    result = prime * result + instance.hashCode();
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
    if (!Arrays.equals(featureTypes, other.featureTypes))
      return false;
    if (instance != other.instance)
      return false;
    if (!Arrays.equals(values, other.values))
      return false;
    return true;
  }
}
