package edu.cmu.ml.rtw.pra.models;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.apache.log4j.Logger;

import cc.mallet.optimize.LimitedMemoryBFGS;
import cc.mallet.optimize.Optimizable;
import cc.mallet.optimize.OptimizationException;
import cc.mallet.optimize.Optimizer;
import cc.mallet.optimize.OrthantWiseLimitedMemoryBFGS;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.MatrixOps;

/**
 * Just a mallet-backed logistic regression classifier, stripped down from one written by Burr
 * Settles for NELL.
 */
public class MalletLogisticRegression {

  protected String name = "Logistic Regression";

  private static Logger log = Logger.getLogger(MalletLogisticRegression.class);
  private Alphabet alphabet;
  private String predicate = "predicate not given";

  private double l1wt = 0.0;
  private double l2wt = 0.0;

  // We store the model vector sparsely in these two parallel arrays, and we keep them sorted by
  // feature ID.  We could put the bias in this vector and give it a special feature ID, but then
  // we'd have to live in rapt terror of somebody coming along and adding something to the aphabet
  // that winds up having the same feature ID.  You shouldn't live in rapt terror; fear is a
  // control mechanism.
  //
  // These members are in use when and only when dParams is null.  If dParams is non-null then we
  // are storing the vectors densely rather than sparsely.
  private int[] sFeatures;
  private double[] sParams;
  private double sBias;

  // Model vector sotred densly.  Element order follows that of our alphabet, except that this
  // array will be one element longer than the alphabet, and that last value will be the bias.
  // This is done to simplify the implementation of training.
  //
  // If this member is null, then we are using sparse vectors stored in sFeatures et al above.
  private double[] dParams;

  // Whether we default to a sparse or dense model after training or when loading
  private boolean preferSparse;

  /**
   * Switch to dense model vector.
   *
   * This is a no-op if we are already dense.
   */
  protected void setDense() {
    if (dParams != null) return;

    log.debug("Converting to dense vectors...");

    // Java will default-initialize all of these elements to zero
    dParams = new double[alphabet.size() + 1];
    for (int i = 0; i < sFeatures.length; i++)
      dParams[sFeatures[i]] = sParams[i];
    dParams[alphabet.size()] = sBias;

    sFeatures = null;
    sParams = null;
    sBias = Double.NaN;
  }

  /**
   * Switch to sparse model vector.
   *
   * This is a no-op if we are already sparse.
   */
  protected void setSparse() {
    if (sParams != null) return;

    log.debug("Converting to sparse vectors...");

    // Sortedness is implicit in dParams, so no need to sort here.
    int numNonZeros = 0;
    for (int i = 0; i < dParams.length - 1; i++) {
      if (dParams[i] < -0.00000001 || dParams[i] > 0.00000001) numNonZeros++;
    }
    sFeatures = new int[numNonZeros];
    sParams = new double[numNonZeros];
    int j = 0;
    for (int i = 0; i < dParams.length - 1; i++) {
      if (dParams[i] < -0.00000001 || dParams[i] > 0.00000001) {
        sFeatures[j] = i;
        sParams[j] = dParams[i];
        j++;
      }
    }
    sBias = dParams[dParams.length - 1];

    dParams = null;
  }

  public MalletLogisticRegression(Alphabet alphabet) {
    this.alphabet = alphabet;
    this.sFeatures = new int[0];
    this.sParams = new double[0];

    // If we spit NaNs, perhaps somebody will notice that we haven't been trained.  Perhaps it
    // would be reasonable to throw an exception instead.  As of yet, nothing really depends on
    // any particular behavior.
    this.sBias = Double.NaN;

    this.dParams = null;

    this.preferSparse = false;
  }

  public MalletLogisticRegression(Alphabet alphabet, boolean preferSparse) {
    this(alphabet);
    this.preferSparse = preferSparse;
  }

  public void train(InstanceList ilist) {
    train(ilist, 999);
  }

  public void train(InstanceList ilist, int numIterations) {
    // make sure alphabets match!
    assert (this.alphabet == ilist.getDataAlphabet());

    // Switch to dense.  And if the bias is not finite, now would be a good time to zero it out.
    setDense();
    if (Double.isNaN(dParams[dParams.length - 1])
        || Double.isInfinite(dParams[dParams.length - 1]))
      dParams[dParams.length - 1] = 0.0;

    int doneIterations = 0;
    boolean converged = false;

    Optimizer optimizer = getOptimizer(ilist);
    for (; !converged && doneIterations < numIterations; doneIterations++) {
      try {
        converged = optimizer.optimize(1);
      } catch (OptimizationException e) {
        log.warn("Catching " + e + "! saying converged.");
        converged = true;
      }
    }

    // if we still have a few iterations left, reset the optimizer and try again... L-BFGS can
    // sometimes eke out more likelihood after the first convergence by re-running without the
    // gradient history.
    converged = false;
    if (doneIterations < numIterations) {
      // Do a reset if we can in order to avoid the additional heap pressure of freeing and
      // reallocating all of its (considerably large) internal state
      if (optimizer instanceof LimitedMemoryBFGS) {
        ((LimitedMemoryBFGS)optimizer).reset();
      } else {
        optimizer = getOptimizer(ilist);
      }
      for (; !converged && doneIterations < numIterations; doneIterations++) {
        try {
          converged = optimizer.optimize(1);
        } catch (OptimizationException e) {
          log.warn("Catching " + e + "! saying converged.");
          converged = true;
        }
      }
    }

    log.info(predicate + ": L-BFGS Converged after " + doneIterations + " iterations!");

    // Back to sparse if need be
    if (preferSparse) {
      setSparse();
      log.debug(sParams.length + " of " + alphabet.size()
                + " parameters were nonzero after training");
    }
  }

  private Optimizer getOptimizer(InstanceList ilist) {
    LogRegOptimizable optimizable = new LogRegOptimizable(ilist);
    // use orthant-wise if there is an L1 term
    if (l1wt > 0.0){
      log.info("Using L1 regularization (l1wt:" + l1wt + ",l2wt:" + l2wt + ")");
      return new OrthantWiseLimitedMemoryBFGS(optimizable, l1wt);
    }
    // otherwise use vanilla L-BFGS
    else{
      log.info("Using L2 regularization (l2wt:" + l2wt + ")");
      return new LimitedMemoryBFGS(optimizable);
    }
  }

  public double classify(final Instance instance) {
    try {
      // If we're sparse...
      if (dParams == null) {
        double sum = sBias;
        final FeatureVector fv = (FeatureVector) instance.getData();
        int i = 0;  // Iterates through fv
        int j = 0;  // Iterates through features/params
        while (i < fv.numLocations() && j < sFeatures.length) {
          int featurei = fv.indexAtLocation(i);
          int featurej = sFeatures[j];
          if (featurei == featurej) {
            sum += fv.valueAtLocation(i) * sParams[j];
            i++;
            j++;
          } else if (featurei < featurej) {
            i++;
          } else {
            j++;
          }
        }
        return sigmoid(sum);
      }

      // If we're dense...
      else {
        double sum = dParams[dParams.length-1];
        FeatureVector fv = (FeatureVector) instance.getData();
        final int size = fv.numLocations();
        for (int fi = 0; fi < size; fi++) {
          sum += fv.valueAtLocation(fi) * dParams[fv.indexAtLocation(fi)];
        }
        return sigmoid(sum);
      }
    } catch (Exception e) {
      throw new RuntimeException("classify(" + instance.getData() + ")", e);
    }
  }

  private double sigmoid(final double sum) {
    return 1.0 / (1 + Math.exp(-sum));
  }

  /**
   * Nested class for optimizing the objective function using L-BFGS.
   */
  private class LogRegOptimizable implements Optimizable.ByGradientValue {

    InstanceList trainingList;

    double[] cachedGradient;
    double cachedValue;
    boolean cachedGradientStale = true;
    boolean cachedValueStale = true;
    int numGetValueCalls = 0;
    int numGetValueGradientCalls = 0;

    public LogRegOptimizable(InstanceList ilist) {
      super();
      this.trainingList = ilist;

      // We've already bet set to dense in the train method by this point, but it comes as no
      // penalty to engage both our belt and our suspenders, and, moreover, it ensures that we
      // don't get caught with our pants down.
      setDense();

      this.cachedGradient = new double[dParams.length];
    }

    @Override
    public double getValue() {

      if (cachedValueStale) {

        numGetValueCalls++;
        cachedValue = 0;
        MatrixOps.setAll(cachedGradient, 0.0);
        cachedGradientStale = true;

        // incorporate likelihood of each data instance
        //
        // n.b. it's worth using "for i" in preference to an iterator to keep heap pressure outside
        // of our tight inner loop
        for (int i = 0; i < trainingList.size(); i++) {
          Instance instance = trainingList.get(i);

          double instanceWeight = trainingList.getInstanceWeight(instance);
          double real = (Double) instance.getTarget();
          double pred = classify(instance);

          assert (!Double.isInfinite(pred));
          double logLikelihood = instanceWeight
              * Math.log((real > 0.5) ? pred : 1 - pred);

          // a few error checks
          if (Double.isNaN(logLikelihood)) {
            log.info("MalletLogisticRegression: NaN - Instance " + instance.getName()
                     + " has instance weight = " + instanceWeight);
          }
          if (Double.isInfinite(logLikelihood)) {
            log.warn("Instance " + instance.getSource()
                     + " has infinite value; skipping value and gradient");
            cachedValue = logLikelihood;
            cachedValueStale = false;
            return logLikelihood;
          }

          FeatureVector fv = (FeatureVector) instance.getData();

          // update the objective value
          cachedValue += logLikelihood;

          // update the gradient
          double dydx = pred * (1 - pred) * (real > 0.5 ? 1.0 / pred : 1.0 / (pred - 1));
          MatrixOps.rowPlusEquals(cachedGradient, dParams.length-1, 0, fv,
                                  instanceWeight * dydx);
          cachedGradient[dParams.length-1] += instanceWeight * dydx;

        }

        // L2 regularization on objective value
        double oValue = cachedValue;
        double l2term = 0;
        for (int fi = 0; fi < dParams.length-1; fi++)
          l2term += dParams[fi] * dParams[fi];
        cachedValue -= l2wt * l2term;

        // L2 regularization on gradient
        MatrixOps.plusEquals(cachedGradient, dParams, -2 * l2wt);

        // bkisiel 2013-01-17: The correlation is spotty sometimes, but disabling this seems to
        // slightly decrease the amount of time our CPU usage falls below the number of threads
        // we're running (which appears to happen from heap / garbage collection contention).  I'm
        // not sure whether logging here sometimes gets things hung up in some synchronizer down in
        // the logging code or whether it's just the reduction in heap pressure from not splicing
        // together the string.
        //
        // log.info("Value (logLik=" + oValue + " l2=" + l2term + ") value=" + cachedValue);
      }

      return cachedValue;

    }

    @Override
    public void getValueGradient(double[] buffer) {
      if (cachedGradientStale) {

        numGetValueGradientCalls++;

        // This also fills in cachedGradient with gradient value
        if (cachedValueStale)
          getValue();

        // reset any bad params to zero
        MatrixOps.substitute(cachedGradient, Double.NEGATIVE_INFINITY, 0.0);
        cachedGradientStale = false;
      }

      assert (buffer != null && buffer.length == dParams.length);
      System.arraycopy(cachedGradient, 0, buffer, 0, cachedGradient.length);
    }

    @Override
    public int getNumParameters() {
      return dParams.length;
    }

    @Override
    public double getParameter(int k) {
      return dParams[k];
    }

    @Override
    public void getParameters(double[] buffer) {
      if (buffer == null || buffer.length != dParams.length) buffer = new double[dParams.length];
      System.arraycopy(dParams, 0, buffer, 0, dParams.length);
    }

    @Override
    public void setParameter(int k, double value) {
      cachedValueStale = true;
      cachedGradientStale = true;
      dParams[k] = value;
    }

    @Override
    public void setParameters(double[] buffer) {
      assert (buffer != null);
      cachedValueStale = true;
      cachedGradientStale = true;
      if (buffer.length != dParams.length)
        dParams = new double[buffer.length];
      System.arraycopy(buffer, 0, dParams, 0, buffer.length);
    }
  }

  public double getL1wt() {
    return l1wt;
  }

  public void setL1wt(double l1wt) {
    this.l1wt = l1wt;
  }

  public double getL2wt() {
    return l2wt;
  }

  public void setL2wt(double l2wt) {
    this.l2wt = l2wt;
  }

  public double[] getSparseParams() {
    boolean isDense = (sParams == null);
    if (isDense) setSparse();
    double[] tmp = sParams;
    if (isDense) setDense();
    return tmp;
  }

  public int[] getSparseFeatures() {
    boolean isDense = (sParams == null);
    if (isDense) setSparse();
    int[] tmp = sFeatures;
    if (isDense) setDense();
    return tmp;
  }

  public double getBias() {
    if (dParams == null) {
      return sBias;
    } else {
      return dParams[dParams.length - 1];
    }
  }
}
