package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Map;

/**
 * Given the results of the path finding random walk (i.e., a map from path types to counts),
 * this object determines which path types to keep, possibly merging some of them.
 */
public interface PathTypeSelector {
  List<PathType> selectPathTypes(Map<PathType, Integer> pathCounts, int numPathsToKeep);
}
