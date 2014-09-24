package edu.cmu.ml.rtw.users.matt.util;

import java.util.Comparator;

public class PairComparator {
  public static <L, R extends Comparable<? super R>> Comparator<Pair<L, R>> negativeRight() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        return -pair1.getRight().compareTo(pair2.getRight());
      }
    };
  }

  public static <L, R extends Comparable<? super R>> Comparator<Pair<L, R>> right() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        return pair1.getRight().compareTo(pair2.getRight());
      }
    };
  }

  public static <L extends Comparable<? super L>, R> Comparator<Pair<L, R>> negativeLeft() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        return -pair1.getLeft().compareTo(pair2.getLeft());
      }
    };
  }

  public static <L extends Comparable<? super L>, R> Comparator<Pair<L, R>> left() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        return pair1.getLeft().compareTo(pair2.getLeft());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> rightThenLeft() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int rightComparison = pair1.getRight().compareTo(pair2.getRight());
        if (rightComparison != 0) return rightComparison;
        return pair1.getLeft().compareTo(pair2.getLeft());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> rightThenNegativeLeft() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int rightComparison = pair1.getRight().compareTo(pair2.getRight());
        if (rightComparison != 0) return rightComparison;
        return -pair1.getLeft().compareTo(pair2.getLeft());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> negativeRightThenLeft() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int rightComparison = -pair1.getRight().compareTo(pair2.getRight());
        if (rightComparison != 0) return rightComparison;
        return pair1.getLeft().compareTo(pair2.getLeft());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> negativeRightThenNegativeLeft() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int rightComparison = -pair1.getRight().compareTo(pair2.getRight());
        if (rightComparison != 0) return rightComparison;
        return -pair1.getLeft().compareTo(pair2.getLeft());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> leftThenRight() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int leftComparison = pair1.getLeft().compareTo(pair2.getLeft());
        if (leftComparison != 0) return leftComparison;
        return pair1.getRight().compareTo(pair2.getRight());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> leftThenNegativeRight() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int leftComparison = pair1.getLeft().compareTo(pair2.getLeft());
        if (leftComparison != 0) return leftComparison;
        return -pair1.getRight().compareTo(pair2.getRight());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> negativeLeftThenRight() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int leftComparison = -pair1.getLeft().compareTo(pair2.getLeft());
        if (leftComparison != 0) return leftComparison;
        return pair1.getRight().compareTo(pair2.getRight());
      }
    };
  }

  public static <L extends Comparable<? super L>, R extends Comparable<? super R>>
      Comparator<Pair<L, R>> negativeLeftThenNegativeRight() {
    return new Comparator<Pair<L, R>>() {
      @Override
      public int compare(Pair<L, R> pair1, Pair<L, R> pair2) {
        int leftComparison = -pair1.getLeft().compareTo(pair2.getLeft());
        if (leftComparison != 0) return leftComparison;
        return -pair1.getRight().compareTo(pair2.getRight());
      }
    };
  }
}
