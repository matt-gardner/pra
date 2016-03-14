package edu.cmu.ml.rtw.pra.features;

import edu.cmu.ml.rtw.pra.graphs.Graph;

/**
 * Represents a path type as a sequence of edges.  This is an abstract base class, allowing for
 * variations on the basic theme of "a path type is a sequence of edge types".
 */
public abstract class BaseEdgeSequencePathTypeFactory implements PathTypeFactory {

  private final Graph _graph;

  public BaseEdgeSequencePathTypeFactory(Graph graph) {
    this._graph = graph;
  }

  protected abstract BaseEdgeSequencePathType newInstance(int[] edgeTypes, boolean[] reverse);

  @Override
  public PathType fromString(String string) {
    return fromString(string, null);
  }

  @Override
  public PathType fromHumanReadableString(String string) {
    return fromString(string, _graph);
  }

  // This method assumes good input!  Do error checking somewhere else, if you need to do any.
  private PathType fromString(String string, Graph graph) {
    // Description is formatted like -1-2-3-4-; doing split("-") would result in an empty string as
    // the first element, so we call substring(1) first.
    String[] parts = string.substring(1).split("-");
    int numHops = parts.length;
    int[] edgeTypes = new int[numHops];
    boolean[] reverse = new boolean[numHops];
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].charAt(0) == '_') {
        reverse[i] = true;
        parts[i] = parts[i].substring(1);
      }
      if (graph == null) {
        edgeTypes[i] = Integer.parseInt(parts[i]);
      } else {
        edgeTypes[i] = graph.getEdgeIndex(parts[i]);
      }
    }
    return newInstance(edgeTypes, reverse);
  }

  @Override
  public PathType[] encode(Path path) {
    boolean[] reverse = path.getReverse();
    int[] edges = path.getEdges();
    PathType[] encoded = new PathType[1];
    encoded[0] = newInstance(edges, reverse);
    return encoded;
  }

  @Override
  public PathType concatenatePathTypes(PathType pathToSource, PathType pathFromTarget) {
    BaseEdgeSequencePathType source = (BaseEdgeSequencePathType) pathToSource;
    BaseEdgeSequencePathType target = (BaseEdgeSequencePathType) pathFromTarget;
    int totalHops = source.numHops() + target.numHops();
    int[] combinedEdgeTypes = new int[totalHops];
    boolean[] combinedReverse = new boolean[totalHops];
    System.arraycopy(source.edgeTypes(), 0, combinedEdgeTypes, 0, source.numHops());
    System.arraycopy(source.reverse(), 0, combinedReverse, 0, source.numHops());

    for (int i = target.numHops() - 1, j = source.numHops(); i >= 0; i--, j++) {
      combinedEdgeTypes[j] = target.edgeTypes()[i];
      combinedReverse[j] = !target.reverse()[i];
    }
    return newInstance(combinedEdgeTypes, combinedReverse);
  }

  @Override
  public PathType addToPathType(PathType pathType, int relation, int node, boolean reverse) {
    BaseEdgeSequencePathType path = (BaseEdgeSequencePathType) pathType;
    int totalHops = path.numHops() + 1;
    int[] combinedEdgeTypes = new int[totalHops];
    boolean[] combinedReverse = new boolean[totalHops];
    System.arraycopy(path.edgeTypes(), 0, combinedEdgeTypes, 0, path.numHops());
    System.arraycopy(path.reverse(), 0, combinedReverse, 0, path.numHops());

    combinedEdgeTypes[totalHops - 1] = relation;
    combinedReverse[totalHops - 1] = reverse;
    return newInstance(combinedEdgeTypes, combinedReverse);
  }
}
