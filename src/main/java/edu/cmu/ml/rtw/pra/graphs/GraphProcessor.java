package edu.cmu.ml.rtw.pra.graphs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;


import edu.cmu.graphchi.ChiFilenames;
import edu.cmu.graphchi.ChiLogger;
import edu.cmu.graphchi.EmptyType;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.preprocessing.EdgeProcessor;
import edu.cmu.graphchi.preprocessing.FastSharder;

/**
 * Just does one thing: processes a graph prior to running GraphChi code on it.
 */
public class GraphProcessor {
  private static Logger logger = ChiLogger.getLogger("graph-processor");

  /**
   * Runs GraphChi's preprocessing (sharding) on the graph.  This produces a number of shard files,
   * and if the files are already present, this is a no-op.  So it's only run once for each graph,
   * no matter how many times you run GraphChi code.
   */
  public void processGraph(String baseFilename, int numShards) throws IOException {
    FastSharder<EmptyType, Integer> sharder = new FastSharder<EmptyType, Integer>(baseFilename, numShards, null,
        new EdgeProcessor<Integer>() {
          public Integer receiveEdge(int from, int to, String token) {
            return Integer.parseInt(token);
          }
        }, null, new IntConverter());
    if (!new File(ChiFilenames.getFilenameIntervals(baseFilename, numShards)).exists()) {
      sharder.shard(new FileInputStream(new File(baseFilename)), "edgelist");
    } else {
      logger.info("Found shards -- no need to pre-process");
    }
  }
}
