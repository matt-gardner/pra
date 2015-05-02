package edu.cmu.ml.rtw.pra.mallet_svm.kernel;

/**
 * <code>KernelManager</code> provides the custom kernel function to <code>svm</code>.
 * @author Syeed Ibn Faiz
 */
public class KernelManager {
    static private CustomKernel customKernel;

    public static CustomKernel getCustomKernel() {
        return customKernel;
    }

    /**
     * Registers the custom kernel
     * @param customKernel 
     */
    public static void setCustomKernel(CustomKernel customKernel) {
        KernelManager.customKernel = customKernel;
    }
    
}
