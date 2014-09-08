package edu.cmu.ml.rtw.users.matt.util;

import java.util.Arrays;

public class Vector {
  private final double[] values;

  public Vector(double[] values) {
    this.values = values;
  }

  public double norm() {
    return Math.sqrt(dotProduct(this));
  }

  public void normalize() {
    double norm = norm();
    for (int i = 0; i < values.length; i++) {
      values[i] = values[i] / norm;
    }
  }

  public Vector multiply(double scalar) {
    double[] newValues = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      newValues[i] = values[i] * scalar;
    }
    return new Vector(newValues);
  }

  public Vector add(Vector other) {
    double[] newValues = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      newValues[i] = values[i] + other.values[i];
    }
    return new Vector(newValues);
  }

  public Vector concatenate(Vector other) {
    double[] newValues = new double[values.length + other.values.length];
    System.arraycopy(values, 0, newValues, 0, values.length);
    System.arraycopy(other.values, 0, newValues, values.length, other.values.length);
    return new Vector(newValues);
  }

  public double dotProduct(Vector other) {
    double dotProduct = 0.0;
    for (int i = 0; i < values.length; i++) {
      dotProduct += values[i] * other.values[i];
    }
    return dotProduct;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
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
    Vector other = (Vector) obj;
    if (!Arrays.equals(values, other.values))
      return false;
    return true;
  }
}
