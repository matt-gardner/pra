package edu.cmu.ml.rtw.pra.graphs;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FileUtil;
import edu.cmu.ml.rtw.users.matt.util.IntTriple;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class RelationSet {

  // Fields dealing with the relations themselves.

  // The file containing the relation triples.
  private final String relationFile;
  // If not null, prepend this prefix to all relation strings in this set.
  private final String relationPrefix;
  // KB relations or surface relations?  The difference is only in the alias relationfinal .
  private final boolean isKb;

  // Fields specific to KB relations.

  // If this is a KB relation set, this file contains the mapping from entities to noun phrases.
  private final String aliasFile;
  // What should we call the alias relation?  Defaults to "alias".  Note that we specify this for
  // each set of _KB_ relations, not for each set of _surface_ relations.  If you want something
  // more complicated, like a different name for each (KB, surface) relation set pair, you'll have
  // to approximate it with relation prefixes.
  private final String aliasRelation;
  // Determines how we try to read the alias file.  We currently allow two values: "freebase" and
  // "nell".  The freebase format is a little complicated; the nell format is just a list of
  // (concept, noun phrase) pairs that we read into a map.  The "nell" format is generic enough
  // that it could be used for more than just NELL KBs, probably.
  private final String aliasFileFormat;

  // Fields specific to surface relations.

  // If this is a surface relation set, if this is true, we only add the alias edges that get
  // generated from the set, not the surface relations themselves.  This was for a random test that
  // I did.
  private final boolean aliasesOnly;

  // Fields for embeddings.

  // The file where we can find embeddings for the relations in this set.
  private final String embeddingsFile;
  // If we are embedding the edges, should we replace the original edges or just augment them?
  private final boolean keepOriginalEdges;

  private final FileUtil fileUtil;

  /**
   * Get the set of aliases specified by this KB relation set.
   */
  public Map<String, List<String>> getAliases() throws IOException {
    if (aliasFile == null) {
      return Maps.newHashMap();
    }
    return getAliasesFromReader(fileUtil.getBufferedReader(aliasFile));
  }

  @VisibleForTesting
  protected Map<String, List<String>> getAliasesFromReader(BufferedReader reader)
  throws IOException {
    if (aliasFileFormat.equals("freebase")) {
      return fileUtil.readMapListFromTsvReader(reader, 3, false, new FileUtil.LineFilter() {
        @Override
        public boolean filter(String[] fields) {
          if (fields.length != 4) return true;
          if (!fields[2].equals("/lang/en")) return true;
          return false;
        }
      });
    } else if (aliasFileFormat.equals("nell")) {
      return fileUtil.readInvertedMapListFromTsvReader(reader, 1000000);
    } else {
      throw new RuntimeException("Unrecognized alias file format");
    }
  }

  public int writeRelationEdgesToGraphFile(FileWriter intEdgeFile,
                                           Set<IntTriple> seenTriples,
                                           String prefixOverride,
                                           Set<String> seenNps,
                                           List<Pair<String, Map<String, List<String>>>> aliases,
                                           Dictionary nodeDict,
                                           Dictionary edgeDict) throws IOException {
    return writeRelationEdgesFromReader(fileUtil.getBufferedReader(relationFile),
                                        loadEmbeddings(),
                                        seenTriples,
                                        prefixOverride,
                                        seenNps,
                                        aliases,
                                        intEdgeFile,
                                        nodeDict,
                                        edgeDict);
  }

  @VisibleForTesting
  protected int writeRelationEdgesFromReader(BufferedReader reader,
                                             Map<String, List<String>> embeddings,
                                             Set<IntTriple> seenTriples,
                                             String prefixOverride,
                                             Set<String> seenNps,
                                             List<Pair<String, Map<String, List<String>>>> aliases,
                                             FileWriter writer,
                                             Dictionary nodeDict,
                                             Dictionary edgeDict) throws IOException {
    System.out.println("Adding edges from relation file " + relationFile);
    String prefix = "";
    if (prefixOverride != null) {
      prefix = prefixOverride;
    } else if (relationPrefix != null) {
      prefix = relationPrefix;
    }
    String line;
    int i = 0;
    int numEdges = 0;
    while ((line = reader.readLine()) != null) {
      fileUtil.logEvery(1000000, ++i);
      String[] fields = line.split("\t");
      String relation;
      String arg1;
      String arg2;
      // TODO(matt): Maybe make this relation file format a configurable field?
      if (isKb) {
        // KB relations are formated (S, O, V).
        arg1 = fields[0];
        arg2 = fields[1];
        relation = fields[2];
      } else {
        // And surface relations are formatted (S, V, O).
        arg1 = fields[0];
        relation = fields[1];
        arg2 = fields[2];
      }
      int ind1 = nodeDict.getIndex(arg1);
      int ind2 = nodeDict.getIndex(arg2);

      if (!isKb) {
        numEdges += addAliasEdges(arg1, ind1, seenNps, writer, nodeDict, edgeDict, aliases);
        numEdges += addAliasEdges(arg2, ind2, seenNps, writer, nodeDict, edgeDict, aliases);
      }

      if (!isKb && aliasesOnly) continue;
      List<String> relationEdges = getEmbeddedRelations(relation, embeddings);
      for (String r : relationEdges) {
        r = prefix + r;
        int rel = edgeDict.getIndex(r);
        writeEdgeIfUnseen(ind1, ind2, rel, seenTriples, writer);
        numEdges++;
      }
    }
    return numEdges;
  }

  private void writeEdgeIfUnseen(int arg1,
                                 int arg2,
                                 int rel,
                                 Set<IntTriple> seenTriples,
                                 FileWriter writer) throws IOException {
    if (seenTriples != null) {
      IntTriple triple = new IntTriple(arg1, arg2, rel);
      if (seenTriples.contains(triple)) return;
      seenTriples.add(triple);
    }
    writer.write(arg1 + "\t" + arg2 + "\t" + rel + "\n");
  }

  @VisibleForTesting
  protected int addAliasEdges(String np,
                              int np_index,
                              Set<String> seenNps,
                              FileWriter writer,
                              Dictionary nodeDict,
                              Dictionary edgeDict,
                              List<Pair<String, Map<String, List<String>>>> aliases)
  throws IOException {
    int numEdges = 0;
    if (seenNps.contains(np)) return numEdges;
    seenNps.add(np);
    for (Pair<String, Map<String, List<String>>> aliasSet : aliases) {
      String aliasRelation = aliasSet.getLeft();
      int aliasIndex = edgeDict.getIndex(aliasRelation);
      Map<String, List<String>> currentAliases = aliasSet.getRight();
      List<String> concepts = currentAliases.get(np);
      if (concepts == null) continue;
      for (String concept : concepts) {
        int concept_index = nodeDict.getIndex(concept);
        writer.write(np_index + "\t" + concept_index + "\t" + aliasIndex + "\n");
        numEdges++;
      }
    }
    return numEdges;
  }

  @VisibleForTesting
  protected List<String> getEmbeddedRelations(String relation, Map<String, List<String>> embeddings) {
    List<String> relationEdges = null;
    if (embeddings != null) {
      relationEdges = embeddings.get(relation);
      if (relationEdges != null && keepOriginalEdges) {
        List<String> relationEdgesCopy = Lists.newArrayList(relationEdges);
        relationEdgesCopy.add(relation);
        relationEdges = relationEdgesCopy;
      }
    }
    if (relationEdges == null) {
      relationEdges = Lists.newArrayList();
      relationEdges.add(relation);
    }
    return relationEdges;
  }

  private Map<String, List<String>> loadEmbeddings() throws IOException {
    Map<String, List<String>> embeddings = null;
    if (embeddingsFile != null) {
      System.out.println("Reading embeddings from file " + embeddingsFile);
      embeddings = fileUtil.readMapListFromTsvFile(embeddingsFile);
    }
    return embeddings;
  }

  public String getRelationFile() {
    return relationFile;
  }

  public String getRelationPrefix() {
    return relationPrefix;
  }

  public boolean getIsKb() {
    return isKb;
  }

  public String getAliasFile() {
    return aliasFile;
  }

  public boolean getAliasesOnly() {
    return aliasesOnly;
  }

  public String getAliasRelation() {
    return aliasRelation;
  }

  public String getEmbeddingsFile() {
    return embeddingsFile;
  }

  public boolean getKeepOriginalEdges() {
    return keepOriginalEdges;
  }

  private RelationSet(Builder builder) {
    relationFile = builder.relationFile;
    relationPrefix = builder.relationPrefix;
    isKb = builder.isKb;
    aliasFile = builder.aliasFile;
    aliasesOnly = builder.aliasesOnly;
    if (builder.aliasRelation == null) {
      aliasRelation = "@ALIAS@";
    } else {
      aliasRelation = builder.aliasRelation;
    }
    aliasFileFormat = builder.aliasFileFormat;
    embeddingsFile = builder.embeddingsFile;
    keepOriginalEdges = builder.keepOriginalEdges;
    fileUtil = builder.fileUtil;
  }

  public static RelationSet fromFile(String filename) throws IOException {
    return fromFile(filename, new FileUtil());
  }

  @VisibleForTesting
  protected static RelationSet fromFile(String filename, FileUtil fileUtil) throws IOException {
    return fromReader(fileUtil.getBufferedReader(filename), fileUtil);
  }

  public static RelationSet fromReader(BufferedReader reader) throws IOException {
    return fromReader(reader, new FileUtil());
  }

  @VisibleForTesting
  protected static RelationSet fromReader(BufferedReader reader, FileUtil fileUtil) throws IOException {
    Builder builder = new Builder();
    builder.setFileUtil(fileUtil);
    String line;
    while ((line = reader.readLine()) != null) {
      String[] fields = line.split("\t");
      String parameter = fields[0];
      String value = fields[1];
      if (parameter.equalsIgnoreCase("relation file")) {
        builder.setRelationFile(value);
      } else if (parameter.equalsIgnoreCase("relation prefix")) {
        builder.setRelationPrefix(value);
      } else if (parameter.equalsIgnoreCase("is kb")) {
        builder.setIsKb(Boolean.parseBoolean(value));
      } else if (parameter.equalsIgnoreCase("alias file")) {
        builder.setAliasFile(value);
      } else if (parameter.equalsIgnoreCase("aliases only")) {
        builder.setAliasesOnly(Boolean.parseBoolean(value));
      } else if (parameter.equalsIgnoreCase("alias relation")) {
        builder.setAliasRelation(value);
      } else if (parameter.equalsIgnoreCase("alias file format")) {
        builder.setAliasFileFormat(value);
      } else if (parameter.equalsIgnoreCase("embeddings file")) {
        builder.setEmbeddingsFile(value);
      } else if (parameter.equalsIgnoreCase("keep original edges")) {
        builder.setKeepOriginalEdges(Boolean.parseBoolean(value));
      }
    }
    return builder.build();
  }

  public static class Builder {
    private String relationFile;
    private String relationPrefix;
    private boolean isKb;
    private String aliasFile;
    private boolean aliasesOnly;
    private String aliasRelation;
    private String aliasFileFormat;
    private String embeddingsFile;
    private boolean keepOriginalEdges;
    private FileUtil fileUtil = new FileUtil();

    public Builder() {}
    public Builder setRelationFile(String r) {this.relationFile = r; return this;}
    public Builder setRelationPrefix(String p) {this.relationPrefix = p; return this;}
    public Builder setIsKb(boolean isKb) {this.isKb = isKb; return this;}
    public Builder setAliasFile(String aliasFile) {this.aliasFile = aliasFile; return this;}
    public Builder setAliasesOnly(boolean a) {this.aliasesOnly = a; return this;}
    public Builder setAliasRelation(String a) {this.aliasRelation = a; return this;}
    public Builder setAliasFileFormat(String a) {this.aliasFileFormat = a; return this;}
    public Builder setEmbeddingsFile(String e) {this.embeddingsFile = e; return this;}
    public Builder setKeepOriginalEdges(boolean k) {this.keepOriginalEdges = k; return this;}
    public Builder setFileUtil(FileUtil f) {this.fileUtil = f; return this;}

    public RelationSet build() {
      return new RelationSet(this);
    }
  }
}
