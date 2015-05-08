package ca.uwo.csd.ai.nlp.common;

import java.io.Serializable;
import java.util.Arrays;

/**
 * <code>SparseVector</code> stores a sparse vector in a memory
 * efficient manner. It stores the elements of a vector
 * as a list of index:value pairs.
 * @author Syeed Ibn Faiz
 */
public class SparseVector implements Serializable {

  public static class Element implements Serializable, Comparable<Element> {
    public int index;
    public double value;

    public Element(int index, double value) {
      this.index = index;
      this.value = value;
    }

    @Override
    public int compareTo(Element o) {
      if (index < o.index) {
        return -1;
      } else if (index > o.index) {
        return 1;
      }
      return 0;
    }
  }

  private Element[] elements;
  private int size;
  private final int MAX_SIZE = 100;

  public SparseVector(int capacity) {
    elements = new Element[capacity];
  }

  public SparseVector() {
    elements = new Element[MAX_SIZE];
  }

  public void add(int index, double value) {
    add(new Element(index, value));
  }

  public void add(Element elem) {
    if (isFull()) {
      resize();
    }
    elements[size++] = elem;
  }

  public Element get(int n) {
    if (n >= size) {
      return null;
    }
    return elements[n];
  }

  public boolean isFull() {
    return size == elements.length;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public int size() {
    return size;
  }

  private void resize() {
    Element[] newElements = new Element[size + MAX_SIZE];
    for (int i = 0; i < elements.length; i++) {
      newElements[i] = elements[i];
      elements[i] = null;
    }
    elements = newElements;
  }

  public void sortByIndices() {
    Arrays.sort(elements, 0, size);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < size; i++) {
      Element element = elements[i];
      sb.append(element.index).append(":").append(element.value).append(" ");
    }
    return sb.toString();
  }
  /**
   * Computes dot product between this vector and the argument <code>vector</code>
   * @param vector
   * @return
   */
  public double dot(SparseVector vector) {
    SparseVector v1 = this;
    SparseVector v2 = vector;
    double result = 0.0;
    int i = 0;
    int j = 0;

    while (i < v1.size() && j < v2.size()) {
      Element e1 = v1.get(i);
      Element e2 = v2.get(j);

      if (e1.index == e2.index) {
        result += e1.value * e2.value;
        i++;
        j++;
      } else if (e1.index < e2.index) {
        i++;
      } else {
        j++;
      }
    }

    return result;
  }

  /**
   * Computes normalized dot product
   * @param vector
   * @return a positive real number in the range [0.0,1.0]
   */
  public double normDot(SparseVector vector) {
    double dot = this.dot(vector);
    double d = Math.sqrt(this.size() * vector.size());
    if (d > 0) {
      dot /= d;
    }
    return dot;
  }

  /**
   * Computes square of the Euclidean distance
   * @param vector
   * @return
   */
  public double squaredDistance(SparseVector vector) {
    SparseVector v1 = this;
    SparseVector v2 = vector;
    double result = 0.0;
    int i = 0;
    int j = 0;

    while (i < v1.size() && j < v2.size()) {
      Element e1 = v1.get(i);
      Element e2 = v2.get(j);

      if (e1.index == e2.index) {
        double d = e1.value - e2.value;
        result += d*d;
        i++;
        j++;
      } else if (e1.index < e2.index) {
        result += e1.value * e1.value;
        i++;
      } else {
        result += e2.value * e2.value;
        j++;
      }
    }

    while (i < v1.size())  {
      Element e1 = v1.get(i);
      result += e1.value * e1.value;
      i++;
    }

    while (j < v2.size()) {
      Element e2 = v2.get(j);
      result += e2.value * e2.value;
      j++;
    }
    return result;
  }

  public void removeDuplicates() {
    int last = 0;
    for (int i = 1; i < size; i++) {
      if (elements[last].index != elements[i].index) {
        last++;
        elements[last] = elements[i];
      }
    }
    size = last + 1;
  }
}
