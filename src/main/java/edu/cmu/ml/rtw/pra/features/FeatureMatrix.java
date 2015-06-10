package edu.cmu.ml.rtw.pra.features;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class FeatureMatrix {
  private final List<MatrixRow> rows;

  public FeatureMatrix(List<MatrixRow> rows) {
    this.rows = rows;
  }

  public int size() {
    return rows.size();
  }

  public List<MatrixRow> getRows() {
    return rows;
  }

  public MatrixRow getRow(int i) {
    return rows.get(i);
  }

  public void shuffle() {
    Collections.shuffle(rows);
  }

  /**
   * Searches the list of matrix rows until one is found with the corresponding source and target.
   * This is O(size), and not recommended for general use.  I added this mostly for easier testing.
   */
  public MatrixRow getSourceTargetRow(int source, int target) {
    for (MatrixRow row : rows) {
      if (row.instance.source() == source && row.instance.target() == target) return row;
    }
    return null;
  }
}
