package edu.cmu.ml.rtw.users.matt.util;

import java.util.Random;

public class FakeRandom extends Random {
    private int nextInt;
    private double nextDouble;

    @Override
    public double nextDouble() {
        return nextDouble;
    }

    @Override
    public int nextInt() {
        return nextInt;
    }

    @Override
    public int nextInt(int max) {
        if (max < nextInt) {
            throw new RuntimeException("Configuration error in FakeRandom...");
        }
        return nextInt;
    }

    public void setNextInt(int nextInt) {
        this.nextInt = nextInt;
    }

    public void setNextDouble(double nextDouble) {
        this.nextDouble = nextDouble;
    }
}
