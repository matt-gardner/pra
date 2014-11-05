package edu.cmu.ml.rtw.pra.features;

public interface PathFollower {
  /**
   * Does whatever prep is needed to compute the feature matrix.  In the RandomWalkPathFollower
   * case, which uses GraphChi, this does the random walks and computes statistics.  In the basic
   * MatrixPathFollower case, this loads connectivity matrices and does the matrix multiplications
   * to get path matrices.
   */
  public void execute();

  /**
   * Now that the feature matrix is ready, get it.  This may involve additional computation, like
   * aggregating statistics in the random walk, or it may just be returning an already built
   * matrix.
   */
  public FeatureMatrix getFeatureMatrix();

  /**
   * It seems that there are sometimes threads that live on and keep java's garbage collection
   * from running as it should on these PathFollowers.  This method should clear any large data
   * structures that are being stored by the object, in addition to doing whatever else is
   * necessary on shut down.
   */
  public void shutDown();

  /**
   * GraphChi for some reason seems to need a wait on the main thread or else it gets deadlocked
   * somehow.  This allows the main thread to see if it should wait or not.
   */
  public boolean usesGraphChi();
}
