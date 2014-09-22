package edu.cmu.ml.rtw.users.matt.util;

public class IntTriple {
  int arg1;
  int arg2;
  int rel;

  public IntTriple(int arg1, int arg2, int rel) {
    this.arg1 = arg1;
    this.arg2 = arg2;
    this.rel = rel;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + arg1;
    result = prime * result + arg2;
    result = prime * result + rel;
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
    IntTriple other = (IntTriple) obj;
    if (arg1 != other.arg1)
      return false;
    if (arg2 != other.arg2)
      return false;
    if (rel != other.rel)
      return false;
    return true;
  }
}
