package edu.cmu.ml.rtw.pra;

/**
 * Determines which rows are acceptable to keep when computing feature matrices.  Primarily used in
 * PathFollowerCompanion, but I made it separate so that it's more easily referenceable and
 * reusable if things change later.
 *
 * Note that this policy does not affect the memory usage of the algorithm; we still keep all
 * observed rows in memory until the matrix is asked for, at which point we filter it before
 * returning it.  TODO?(matt): Maybe it would be worth it to change this, to check for whether a
 * row is acceptable before aggregating counts?  It would save some memory at the cost of increased
 * processing time while doing the walks.  I'm not sure that's worth it, as we currently don't have
 * any problems with this taking too much memory.
 */
public enum MatrixRowPolicy {
    // Any row is acceptable; keep everything that is found.
    EVERYTHING,
    // Only keep rows that correspond to (source, target) pairs where the target is a "known"
    // target node.  This either means that the node was in the list of training examples, or that
    // the node was in the "allowedTargets" set passed into the PathFollower.
    ALL_TARGETS,
    // Only keep rows that correspond to (source, target) pairs in the training data.  This is
    // obviously only useful during training time, and not testing time.
    PAIRED_TARGETS_ONLY,
}
