package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Map;

import edu.cmu.ml.rtw.users.matt.util.MapUtil;

public class MostFrequentPathTypeSelector implements PathTypeSelector {

  @Override
  public List<PathType> selectPathTypes(Map<PathType, Integer> pathCounts, int numPathsToKeep) {
    System.out.println("SELECTING PATH TYPES - MostFrequentPathTypeSelector");
    if (numPathsToKeep == -1) {
      return MapUtil.getKeysSortedByValue(pathCounts, true);
    }
    return MapUtil.getTopKeys(pathCounts, numPathsToKeep);
  }
}
