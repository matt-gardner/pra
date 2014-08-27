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

    public void outputToFile(String filename) throws IOException {
        outputToWriter(new FileWriter(filename));
    }

    public void outputToWriter(FileWriter writer) throws IOException {
        for (MatrixRow row : rows) {
            writer.write(row.sourceNode + "," + row.targetNode + "\t");
            for (int i=0; i<row.columns; i++) {
                writer.write(row.pathTypes[i] + "," + row.values[i] + " -#- ");
            }
            writer.write("\n");
        }
        writer.close();
    }
}
