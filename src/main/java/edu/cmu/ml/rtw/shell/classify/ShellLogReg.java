package edu.cmu.ml.rtw.shell.classify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Scanner;
import java.util.TreeMap;

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
import cc.mallet.types.RankedFeatureVector;
import cc.mallet.types.SparseVector;

import edu.cmu.ml.rtw.util.Sort;

/**
 * Elastic-net logistic regression implementation of a ShellClassifier.
 * 
 * @author bsettles
 *
 * 2013-05-06: This can now store model vectors dense or sparse at classification time.  Dense turns
 * out to be decidedly faster for situations todate where the sparsity is not all that great.  A lot
 * of the code here was originally written for sparse model vectors, and not all operations are as
 * well-implemented as they could be (e.g. getSparseFeatures is pretty sloppy when we happen to have
 * a dense vector).  As usual, doing the best job we can think of is left for future work on an
 * as-needed basis.
 */
public class ShellLogReg extends ShellClassifier {

    protected String name = "Logistic Regression";

    private static Logger log = Logger.getLogger(ShellLogReg.class);

    private double l1wt = 0.0;
    private double l2wt = 10.0;

    // We store the model vector sparsely in these two parallel arrays, and we keep them sorted by
    // feature ID.  We could put the bias in this vector and give it a special feature ID, but then
    // we'd have to live in rapt terror of somebody coming along and adding something to the aphabet
    // that winds up having the same feature ID.  You shouldn't live in rapt terror; fear is a
    // control mechanism.
    private int[] sFeatures;
    private double[] sParams;
    private double sBias;

    // Model vector sotred densly.  Element order follows that of our alphabet, except that this
    // array will be one element longer than the alphabet, and that last value will be the bias.
    // This is done to simplify the implementation of training.
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

    public ShellLogReg(Alphabet alphabet) {
        super(alphabet);
        this.sFeatures = new int[0];
        this.sParams = new double[0];

        // If we spit NaNs, perhaps somebody will notice that we haven't been trained.  Perhaps it
        // would be reasonable to throw an exception instead.  As of yet, nothing really depends on
        // any particular behavior.
        this.sBias = Double.NaN;

        this.dParams = null;

        this.preferSparse = true;
    }

    public ShellLogReg(Alphabet alphabet, boolean preferSparse) {
        this(alphabet);
        this.preferSparse = preferSparse;
    }

    /**
     * The "data" paylod of instance must be a FeatureVector with sorted such that feature ids are
     * in increasing order.
     */
    @Override
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

    @Override
    public void train(InstanceList ilist) {
        train(ilist, 999);
    }

    @Override
    public void compact() {
        // Nothing to be compacted anymore
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

        // if we still have a few iterations left, reset the optimizer and try
        // again... L-BFGS can sometimes eke out more likelihood after the first
        // convergence by re-running without the gradient history.
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

        // Back to sparse if need be
        if (preferSparse) {
            setSparse();
            log.debug(sParams.length + " of " + alphabet.size()
                    + " parameters were nonzero after training");
        }
    }

    private double sigmoid(final double sum) {
        return 1.0 / (1 + Math.exp(-sum));
    }

    private Optimizer getOptimizer(InstanceList ilist) {
        LogRegOptimizable optimizable = new LogRegOptimizable(ilist);
        // use orthant-wise if there is an L1 term
        if (l1wt > 0.0)
            return new OrthantWiseLimitedMemoryBFGS(optimizable, l1wt);
        // otherwise use vanilla L-BFGS
        else
            return new LimitedMemoryBFGS(optimizable);
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
                // n.b. it's worth using "for i" in preference to an iterator to keep heap pressure
                // outside of our tight inner loop
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
                        log.info("ShellLogReg: NaN - Instance " + instance.getName()
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

                // bkisiel 2013-01-17: The correlation is spotty sometimes, but disabling this seems
                // to slightly decrease the amount of time our CPU usage falls below the number of
                // threads we're running (which appears to happen from heap / garbage collection
                // contention).  I'm not sure whether logging here sometimes gets things hung up in
                // some synchronizer down in the logging code or whether it's just the reduction in
                // heap pressure from not splicing together the string.
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
            if (buffer == null || buffer.length != dParams.length)
                buffer = new double[dParams.length];
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

    /* bkdb
    public void setParams(double[] params) {
        assert (params.length == this.params.length);
        this.params = params;
    }
    */

    protected ArrayList<Integer> getRankedValues(final double[] values) {
        // FODO: This seems to cause the heap some grief and to bottleneck multithreaded running.
        // We should probably switch do a different way of sorting that doesn't rely on
        // constructing an Integer for everything, but maybe that's something to come back to after
        // we're sure that we're not going to marginalize the problem by cutting down the feature
        // space.

        // Initialize rankedValues
        ArrayList<Integer> rankedValues = new ArrayList<Integer>(values.length);
        for (int i = 0; i < values.length; i++) {
            // the model files, for instance, go out to 8 decimal places.  That seems like
            // sufficient justification to omit any values that would come out that way as all zeros
            // anyway.
            if (values[i] >= 0.00000001 || values[i] <= -0.00000001) rankedValues.add(i);
        }

        // Sort using a special comparator that compares the values in values rather than the
        // indexes in rankedValues.  And one that also results in descending, rather than ascending.
        Collections.sort(rankedValues, new Comparator() {
                public int compare(Object o1, Object o2) {
                    Integer i1 = (Integer)o1;
                    Integer i2 = (Integer)o2;
                    return -Double.compare(values[i1], values[i2]);
                }
            });

        return rankedValues;
    }

    @Override
    public String getModelSummary() {
        return getModelSummary(40);
    }

    public String getModelSummary(int numToReturn) {
        // First thing we need to do is come up with a version of features and params that is sorted
        // by the parameter value so that we can use the most extreme features in our summary.  We
        // used to keep a copy of this around, but we no longer do this because we no longer use
        // this kind of sorting for any other purpose.

        int numFeatures;
        int[] sortedFeatures;
        double[] sortedParams;
        if (dParams == null) {
            numFeatures = sFeatures.length;
            sortedFeatures = new int[numFeatures];
            System.arraycopy(sFeatures, 0, sortedFeatures, 0, numFeatures);
            sortedParams = new double[numFeatures];
            System.arraycopy(sParams, 0, sortedParams, 0, numFeatures);
        } else {
            int numNonZeros = 0;
            for (int i = 0; i < dParams.length - 1; i++) {
                if (dParams[i] < -0.00000001 || dParams[i] > 0.00000001) numNonZeros++;
            }
            numFeatures = numNonZeros;
            sortedFeatures = new int[numNonZeros];
            sortedParams = new double[numNonZeros];
            int j = 0;
            for (int i = 0; i < dParams.length - 1; i++) {
                if (dParams[i] < -0.00000001 || dParams[i] > 0.00000001) {
                    sortedFeatures[j] = i;
                    sortedParams[j] = dParams[i];
                    j++;
                }
            }
        }
            
        Sort.heapify(sortedParams, sortedFeatures);
        Sort.heapsort(sortedParams, sortedFeatures);

        // Use up to 1/3 of our allotment (rounding down) on the most heavily-weighted negative
        // features.
        StringBuffer sb = new StringBuffer();
        int numUsed = 0;
        int numNegativesToUse = numToReturn / 3;
        for (int rank = 0; rank < numFeatures && numUsed < numNegativesToUse; rank++) {
            int feature = sortedFeatures[rank];
            double value = sortedParams[rank];
            if (value >= 0.0) break;
            if (value > -0.000001) continue;
            sb.append(alphabet.lookupObject(feature) + String.format("\t%.5f\t", value));
            numUsed++;
        }

        // The remainder go to the most heavily-weighted positive features.  If we don't use up our
        // entire allotment, that's OK; this is just a summary and we're making a decent effort at
        // it.
        for (int rank = numFeatures - (numToReturn - numUsed) - 1; rank < numFeatures && numUsed < numToReturn; rank++) {
            if (rank < 0) {
                if (numFeatures == 0) break;
                rank = 0;
            }
            int feature = sortedFeatures[rank];
            double value = sortedParams[rank];
            if (value < 0.000001) continue;
            sb.append(alphabet.lookupObject(feature) + String.format("\t%.5f\t", value));
            numUsed++;
        }

        return sb.toString().trim();
    }

    @Override
    public String getProvenance(Instance instance) {
        return getProvenance(instance, 10);
    }

    public String getProvenance(Instance instance, int numToReturn) {

        // This might be too slow and ugly, but we can come back and make it more lean and mean as
        // needed.  One thing we could do that might be really clever is to just keep running lists
        // of our most extreme features rather than building up the whole dot product, guessing at
        // its ultimate length, sorting, etc.
        //
        // What we'll do instead is just make a products array that is parallel to our existing
        // features array, and accept that some or all of it might wind up zero because we don't
        // emit any products that are too close to zero anyway.

        int numFeatures;
        double[] products;
        int[] sortedFeatures;

        if (dParams == null) {
            numFeatures = sFeatures.length;
            products = new double[numFeatures];
            FeatureVector fv = (FeatureVector) instance.getData();
            int i = 0;  // Iterates through fv
            int j = 0;  // Iterates through features/params
            while (i < fv.numLocations() && j < numFeatures) {
                int featurei = fv.indexAtLocation(i);
                int featurej = sFeatures[j];
                if (featurei == featurej) {
                    products[j] = fv.valueAtLocation(i) * sParams[j];
                    i++;
                    j++;
                } else if (featurei < featurej) {
                    i++;
                } else {
                    j++;
                }
            }
            sortedFeatures = new int[numFeatures];
            System.arraycopy(sFeatures, 0, sortedFeatures, 0, numFeatures);
        } else {
            numFeatures = dParams.length-1;
            products = new double[numFeatures];
            sortedFeatures = new int[numFeatures];
            FeatureVector fv = (FeatureVector) instance.getData();
            for (int i = 0; i < fv.numLocations(); i++) {
                int di = fv.indexAtLocation(i);
                products[i] = fv.valueAtLocation(i) * dParams[di];
                sortedFeatures[i] = di;
            }
        }

        // Make a copy of our features to sort with and sort
        Sort.heapify(products, sortedFeatures);
        Sort.heapsort(products, sortedFeatures);

        // Use up to 1/3 of our allotment (rounding down) on the most heavily-weighted negative
        // features.
        StringBuffer sb = new StringBuffer();
        int numUsed = 0;
        int numNegativesToUse = numToReturn / 3;
        for (int rank = 0; rank < numFeatures && numUsed < numNegativesToUse; rank++) {
            int feature = sortedFeatures[rank];
            double value = products[rank];
            if (value >= 0.0) break;
            if (value > -0.000001) continue;
            sb.append(alphabet.lookupObject(feature) + String.format("\t%.5f\t", value));
            numUsed++;
        }

        // The remainder go to the most heavily-weighted positive features.  If we don't use up our
        // entire allotment, that's OK; this is just a summary and we're making a decent effort at
        // it.
        for (int rank = numFeatures - (numToReturn - numUsed) - 1; rank < numFeatures && numUsed < numToReturn; rank++) {
            int feature = sortedFeatures[rank];
            double value = products[rank];
            if (value < 0.0) break;
            if (value < 0.000001) continue;
            sb.append(alphabet.lookupObject(feature) + String.format("\t%.5f\t", value));
            numUsed++;
        }

        return sb.toString().trim();
    }

    /**
     * Replace this classifier's model (if any) with the one in the given file
     *
     * The model file contains feature IDs that must be interpreted according to the alphabet with
     * which it was written.  It is the caller's responsibility to manage that.  If the current
     * alphabet is different, then the caller is responsible for supplying a non-null alphabetMap
     * parameter.  The value of alphabetMap[i] will be consulted to determine the new feature ID to
     * use for feature "i" in the old alphabet.  A negative value can be used to indicate a feature
     * ID that is no longer present in the current alphabet; such features will be absent from the
     * loaded model.  The alphabetMap parameter may be left null if the model alphabet is the same
     * as the current alphabet; if it is null, then no ID translation will be performed.
     *
     * Building of the alphabetMap parameter is left for the caller to do because it may well be the
     * case that a single alphabet has been used for multiple models, and we wouldn't want to waste
     * the time and space to have an alphabet and/or translation map for each model in that case.
     */
    public void readModelFromFile(File modelFile, int[] alphabetMap) {
        try { 
            // The first line indicates how many features are in the model.  We'll start out sizing
            // the model according to that.  If we have to discard any features that are no longer
            // in the alphabet, then we'll just have to downsize the model afterward.  The main
            // thing is to avoid building any kind of a data structure where we have to accumulate
            // Integer, Double, or String objects for each feature because that gets really ugly
            // when there are millions of features, and the heap activity gets to be bad enough to
            // prevent multithreaded operation even though we're not synchronizing on anything here.

            // The below imlementation is geared to sparse model vector operation.  We can add an
            // alternate implementation as needed for the case that we'd prefer a dense model.

            BufferedReader br = new BufferedReader(new FileReader(modelFile));
            String line = br.readLine();
            if (line == null) {
                log.debug(modelFile + " is empty.  Setting empty model vector with a bias of NaN");
                sFeatures = new int[0];
                sParams = new double[0];
                sBias = Double.NaN;
                return;
            }
            int numFeatures = Integer.parseInt(line);
            int numFeaturesFound = 0;
            int numFeaturesUsed = 0;
            sFeatures = new int[numFeatures];
            sParams = new double[numFeatures];

            // Next line is the bias
            line = br.readLine();
            if (line == null) {
                log.warn(modelFile + " ends before bias.  Setting a bias of NaN");
                sBias = Double.NaN;
            } else {
                sBias = Double.parseDouble(line);
            }

            // Eat up the features.  Pay attention to the order of feature IDs so that we sort the
            // model if they're out of order, and so that we don't waste time sorting an
            // already-sorted model if it happens to be already-sorted.
            boolean alreadySorted = true;
            int prevFeatureID = -1;
            while (true) {
                line = br.readLine();
                if (line == null) break;
                numFeaturesFound++;
                if (numFeaturesFound > numFeatures)
                    throw new RuntimeException(modelFile + " has more than the " + numFeatures
                            + " features it claims to have");

                int pos = line.indexOf("\t");
                int feature = Integer.parseInt(line.substring(0, pos));
                double weight = Double.parseDouble(line.substring(pos+1));

                if (alphabetMap != null) {
                    feature = alphabetMap[feature];
                    if (feature < 0) continue;
                }
                if (feature < prevFeatureID)
                    alreadySorted = false;
                else
                    prevFeatureID = feature;

                sFeatures[numFeaturesUsed] = feature;
                sParams[numFeaturesUsed] = weight;
                numFeaturesUsed++;
            }
            br.close();

            if (numFeaturesFound < numFeatures)
                throw new RuntimeException(modelFile + " has fewer than the " + numFeatures
                        + " features it claims to have");
            
            // Resize model down if we didn't use all of the space we'd allocated
            if (numFeaturesUsed < sFeatures.length) {
                int[] newFeatures = new int[numFeaturesUsed];
                System.arraycopy(sFeatures, 0, newFeatures, 0, numFeaturesUsed);
                sFeatures = newFeatures;
                double[] newParams = new double[numFeaturesUsed];
                System.arraycopy(sParams, 0, newParams, 0, numFeaturesUsed);
                sParams = newParams;
            }

            log.debug(modelFile + ": loaded " + numFeaturesUsed + " of " + numFeatures
                    + " features.  Sorting...");

            // Sort model if we need to
            if (!alreadySorted) {
                log.debug(modelFile + ": sorting...");
                Sort.heapify(sFeatures, sParams);
                Sort.heapsort(sFeatures, sParams);
            }

            if (!preferSparse) setDense();
        } catch (Exception e) {
            throw new RuntimeException("readModelFromFile(" + modelFile + ")", e);
        }
    }

    @Override
    public void writeModelToFile(File modelFile) {
        try {
            FileWriter fw = new FileWriter(modelFile);
            try {
                if (dParams == null) {
                    // First line is number of features
                    fw.write(sFeatures.length + "\n");

                    // Next line is bias
                    fw.write(String.format("%.8f\n", sBias));
                
                    // And then all the features.  We used to sort by score, but that was when we were
                    // including the feature strings in the model file, which made loading large models
                    // too expensive.  Because we're only emitting feature IDs now, visual inspection of
                    // the model files is no longer very useful, so there's not much sense in wasting
                    // the time and effort to sort the features in any particular way.
                    //
                    // Actually, our features should already be sorted by feature ID, and that's really
                    // the best option because then we won't need to resort after reloading.

                    for (int i = 0; i < sFeatures.length; i++)
                        fw.write(String.format("%d\t%.8f\n", sFeatures[i], sParams[i]));
                } else {
                    // See comments above.

                    int numNonZeros = 0;
                    for (int i = 0; i < dParams.length - 1; i++) {
                        if (dParams[i] < -0.00000001 || dParams[i] > 0.00000001) numNonZeros++;
                    }

                    fw.write(numNonZeros + "\n");
                    fw.write(String.format("%.8f\n", dParams[dParams.length - 1]));

                    for (int i = 0; i < dParams.length - 1; i++) {
                        if (dParams[i] < -0.00000001 || dParams[i] > 0.00000001) {
                            fw.write(String.format("%d\t%.8f\n", i, dParams[i]));
                        }
                    }
                }
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("writeModelToFile(" + modelFile + ")", e);
        }
    }
}
