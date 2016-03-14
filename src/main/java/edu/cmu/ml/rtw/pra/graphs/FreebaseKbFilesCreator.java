package edu.cmu.ml.rtw.pra.graphs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.mattg.util.FileBuffer;

/**
 * Creates a set of PRA files for a Freebase KB (including a graph file, a set of category files,
 * relation files, and other necessary metadata files).  Note that this does _not_ produce a
 * graph_chi/ directory, with a graph intended for direct use by GraphChi.  This is intended mostly
 * to be used as input, to save the need to produce Freebase metadata every time you want to build
 * a new graph that uses Freebase.
 */
public class FreebaseKbFilesCreator {

  @SuppressWarnings("unused")
  private static Logger log = Logger.getLogger(FreebaseKbFilesCreator.class);

  private static final int BUFFER_SIZE = 200;

  public static void createGraphChiRelationGraph(String kb_filename, String outdir) throws IOException {
    // Some preparatory stuff
    if (!outdir.endsWith("/")) {
      outdir += "/";
    }
    if (new File(outdir).exists()) {
      System.out.println("Out directory already exists! Exiting...");
      System.exit(-1);
    }
    new File(outdir).mkdirs();

    String relation_dir = outdir + "relations/";
    new File(relation_dir).mkdirs();
    String categories_dir = outdir + "category_instances/";
    new File(categories_dir).mkdirs();
    FileWriter string_edge_file = new FileWriter(outdir + "labeled_edges.tsv");

    getFreebaseMetadata(kb_filename, outdir);
    getFreebaseInstances(kb_filename, outdir, relation_dir, categories_dir, string_edge_file);
    string_edge_file.close();
  }

  public static void getFreebaseMetadata(String kb_filename, String base_dir) throws IOException {
    System.out.println("Getting relation inverses and ranges");
    FileWriter inverses_out = new FileWriter(base_dir + "inverses.tsv");
    FileWriter ranges_out = new FileWriter(base_dir + "ranges.tsv");
    FileWriter domains_out = new FileWriter(base_dir + "domains.tsv");
    String kb_base = kb_filename.substring(0, kb_filename.lastIndexOf("/")+1);
    String schema_file_name = kb_base + "schema.tsv";
    String line;
    BufferedReader reader = new BufferedReader(new FileReader(schema_file_name));
    while ((line = reader.readLine()) != null) {
      String[] fields = line.split("\t");
      if (fields[0].equals("inverse")) {
        inverses_out.write(fields[1] + "\t" + fields[2] + "\n");
      }
      if (fields[0].equals("argtypes")) {
        String relation = fields[1];
        String domain = fields[2];
        String range = fields[3];
        ranges_out.write(relation + "\t" + range + "\n");
        domains_out.write(relation + "\t" + domain + "\n");
      }
    }
    inverses_out.close();
    ranges_out.close();
    domains_out.close();
  }

  public static void getFreebaseInstances(String kb_filename,
                                          String base_dir,
                                          String relation_dir,
                                          String categories_dir,
                                          FileWriter string_edge_file) throws IOException {
    // Basic outline: we just iterate over all of the relations in the kb file.  Each edge there is
    // a relation that should be added, and some of the edges also are category edges that we use
    // to populate some auxiliary files.
    System.out.println("Getting Freebase relation instances");
    BufferedReader reader = new BufferedReader(new FileReader(kb_filename));
    String line;
    int i = 0;
    Map<String, FileBuffer> relation_files = new HashMap<String, FileBuffer>();
    Map<String, FileBuffer> category_files = new HashMap<String, FileBuffer>();
    while ((line = reader.readLine()) != null) {
      if (++i % 1000000 == 0) {
        System.out.println(i);
      }
      String[] parts = line.split("\t");
      String arg1 = parts[0];
      String arg2 = parts[2];
      String relation = parts[1];
      // First we write the category / relation instance to the file holding instances of that
      // relation.
      if (relation.equals("/type/object/type")) {
        // This is a category relation, so we save it in a category file, not a relation file.
        FileBuffer writer = category_files.get(arg2);
        if (writer == null) {
          writer = new FileBuffer(categories_dir + arg2.replace("/", "_"), BUFFER_SIZE);
          category_files.put(arg2, writer);
        }
        writer.writeLine("" + arg1);
      } else {
        FileBuffer writer = relation_files.get(relation);
        if (writer == null) {
          String fixed = relation.replace("/", "_");
          writer = new FileBuffer(relation_dir + fixed, BUFFER_SIZE);
          relation_files.put(relation, writer);
        }
        writer.writeLine(arg1 + "\t" + arg2);
      }
      // Then we write the relation as a new line in our graph edge file.
      string_edge_file.write(arg1 + "\t" + arg2 + "\t" + relation + "\n");
    }
    reader.close();
    for (FileBuffer buffer : category_files.values()) {
      buffer.flush();
    }
    for (FileBuffer buffer : relation_files.values()) {
      buffer.flush();
    }
  }

  // I changed the name of this so that sbt wouldn't list it as an option every time you want to
  // run an experiment.  If you actually _do_ want to create relation metadata for Freebase, just
  // change this name base to main and run it through sbt.
  public static void NOT_main(String[] args) throws Exception {
    if (args.length != 2) {
      System.out.println("Usage: edu.cmu.ml.rtw.pra.GraphCreator [kb_file] [outdir]");
    }
    String kb_filename = args[0];
    String base_dir = args[args.length-1];

    createGraphChiRelationGraph(kb_filename, base_dir);
  }
}
