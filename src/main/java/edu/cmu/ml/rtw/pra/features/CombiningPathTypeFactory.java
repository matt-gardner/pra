package edu.cmu.ml.rtw.pra.features;

import java.util.Collection;

/**
 * A PathTypeFactory that also knows how to combine multiple PathTypes into a single
 * representation.
 */
public interface CombiningPathTypeFactory extends PathTypeFactory {

  /**
   * Takes a collection of PathTypes and return a single PathType that combines elements from all
   * of them.  This could, for instance, do an OR between all indices in the PathTypes, or it could
   * find the mean of the vectors represented by each edge in the PathTypes, or any number of other
   * things.  There may be restrictions on the sets of PathTypes that are supported (for instance,
   * a set of PathTypes of differing lengths might throw an IllegalArgumentException).
   */
  public PathType combinePathTypes(Collection<PathType> pathTypes);
}
