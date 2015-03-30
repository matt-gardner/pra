package edu.cmu.ml.rtw.pra.features;

/**
 * Determines which rows are acceptable to keep when computing feature matrices.  Primarily used in
 * PathFollowerCompanion, but I made it separate so that it's more easily referenceable and
 * reusable if things change later.
 *
 * Note that this policy does not affect the memory usage of the algorithm; we still keep all
 * observed rows in memory until the matrix is asked for, at which point we filter it before
 * returning it.  It might be worth it to change this someday, to check for whether a row is
 * acceptable before aggregating counts.  It would save some memory at the cost of increased
 * processing time while doing the walks.  Currently, that's not really an issue, however, so we
 * don't do it.
 */
public enum MatrixRowPolicy {
  // Any row is acceptable; keep everything that is found.
  EVERYTHING,
  // Only keep rows that correspond to (source, target) pairs where the target is a "known" target
  // node.  This either means that the node was in the list of training examples, or that the node
  // was in the "allowedTargets" set passed into the PathFollower.
  ALL_TARGETS,
  // Only keep rows that correspond to (source, target) pairs given in the data.  This should only
  // be used when you have both positive and negative instances given both at training and at test
  // time.
  PAIRED_TARGETS_ONLY;

  public static MatrixRowPolicy parseFromString(String policy) {
    if (policy.equalsIgnoreCase("everything")) {
      return EVERYTHING;
    } else if (policy.equalsIgnoreCase("all-targets")) {
      return ALL_TARGETS;
    } else if (policy.equalsIgnoreCase("paired-targets-only")) {
      return PAIRED_TARGETS_ONLY;
    }
    throw new RuntimeException("Unrecognized matrix row policy: " + policy);
  }
}
