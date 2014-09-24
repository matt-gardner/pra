package edu.cmu.ml.rtw.users.matt.util;

import java.io.Serializable;

public class Pair<L, R> implements Serializable {
  private static final long serialVersionUID = 1L;

  protected final L left;
  protected final R right;

  public Pair(final L left, final R right) {
    this.left = left;
    this.right = right;
  }

  public static <LT, RT> Pair<LT, RT> makePair(LT left, RT right) {
    return new Pair<LT, RT>(left, right);
  }

  public R getRight() {
    return right;
  }

  public L getLeft() {
    return left;
  }

  public String toString() {
    String right = (getRight() == null) ? null : getRight().toString();
    String left = (getLeft() == null) ? null : getLeft().toString();
    return String.format("(%s,%s)", left, right);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
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
    Pair<?, ?> other = (Pair<?, ?>) obj;
    if (left == null) {
      if (other.left != null)
        return false;
    } else if (!left.equals(other.left))
      return false;
    if (right == null) {
      if (other.right != null)
        return false;
    } else if (!right.equals(other.right))
      return false;
    return true;
  }
}
