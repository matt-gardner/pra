package edu.cmu.ml.rtw.pra.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;

public class GraphConfig {
  public final String outdir;
  public final List<RelationSet> relationSets;

  // Edge deduplication is optional because it makes us store each edge that's written in memory,
  // so we can see if we've already got the edge, and it takes more time because for every edge we
  // have to query a hash set to see if we've already written it.  If you know that you aren't
  // going to have any duplicate edges in your relation sets, it will be quicker and less
  // memory-intensive to just leave deduplicateEdges false.
  public final boolean deduplicateEdges;
  public final boolean createMatrices;
  public final int maxMatrixFileSize;

  private GraphConfig(Builder builder) {
    outdir = builder.outdir;
    relationSets = builder.relationSets;
    deduplicateEdges = builder.deduplicateEdges;
    createMatrices = builder.createMatrices;
    maxMatrixFileSize = builder.maxMatrixFileSize;
  }

  public static class Builder {
    private String outdir;
    private List<RelationSet> relationSets = Lists.newArrayList();
    private boolean deduplicateEdges = false;
    private boolean createMatrices = false;
    private int maxMatrixFileSize = -1;

    public Builder() {}
    public Builder setOutdir(String x) {outdir = x; return this;}
    public Builder setRelationSets(List<RelationSet> x) {relationSets = x; return this;}
    public Builder setDeduplicateEdges(boolean x) {deduplicateEdges = x; return this;}
    public Builder setCreateMatrices(boolean x) {createMatrices = x; return this;}
    public Builder setMaxMatrixFileSize(int x) {maxMatrixFileSize = x; return this;}

    public GraphConfig build() {
      return new GraphConfig(this);
    }

    public void setFromSpecFile(BufferedReader reader) throws IOException {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split("\t");
        String parameter = fields[0];
        String value = fields[1];
        if (parameter.equalsIgnoreCase("relation set")) {
          relationSets.add(RelationSet.fromFile(value));
        } else if (parameter.equalsIgnoreCase("deduplicate edges")) {
          setDeduplicateEdges(Boolean.parseBoolean(value));
        } else if (parameter.equalsIgnoreCase("create matrices")) {
          setCreateMatrices(Boolean.parseBoolean(value));
        } else if (parameter.equalsIgnoreCase("max matrix file size")) {
          setMaxMatrixFileSize(Integer.parseInt(value));
        } else {
          throw new RuntimeException("Unrecognized graph spec option: " + line);
        }
      }
    }
  }
}
