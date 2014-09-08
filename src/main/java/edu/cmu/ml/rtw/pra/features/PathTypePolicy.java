package edu.cmu.ml.rtw.pra.features;

/**
 * Determines which (source, target) pairs are acceptable to keep when counting path types.
 * Primarily used in PathFinderCompanion, but I made it separate so that it's more easily
 * referenceable and reusable if things change later.
 *
 * Note that in the path finder, we start walks from all source and target nodes in the training
 * data.  The question is, if we find a path between a source and a target node that are not
 * paired, do we count it?
 */
public enum PathTypePolicy {
  // Accept all path types from any source to any target, no matter which (source, target) pair it
  // is.
  EVERYTHING,
  // Only keep paths that connect (source, target) pairs in the training data.
  PAIRED_ONLY;

  public static PathTypePolicy parseFromString(String policy) {
    if (policy.equals("everything")) {
      return EVERYTHING;
    } else if (policy.equals("paired-only")) {
      return PAIRED_ONLY;
    }
    throw new RuntimeException("Unrecognized path type policy: " + policy);
  }
}
