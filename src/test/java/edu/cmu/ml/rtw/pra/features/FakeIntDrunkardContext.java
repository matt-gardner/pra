package edu.cmu.ml.rtw.pra.features;

import junit.framework.TestCase;

import edu.cmu.graphchi.preprocessing.VertexIdTranslate;
import edu.cmu.graphchi.walks.IntDrunkardContext;

public class FakeIntDrunkardContext implements IntDrunkardContext {
    private boolean expectReset;
    private int expectedWalk;
    private int expectedNextVertex;
    private boolean expectedTrackBit;
    private boolean tested = false;
    private boolean dieIfCalled = false;
    private boolean walkStartedFromVertex = false;

    public void setExpectations(boolean expectReset,
                                int expectedWalk,
                                int expectedNextVertex,
                                boolean expectedTrackBit) {
        this.expectReset = expectReset;
        this.expectedWalk = expectedWalk;
        this.expectedNextVertex = expectedNextVertex;
        this.expectedTrackBit = expectedTrackBit;
    }

    public void setExpectationsForReset(int expectedWalk,
                                        boolean expectedTrackBit) {
        this.expectReset = true;
        this.expectedWalk = expectedWalk;
        this.expectedTrackBit = expectedTrackBit;
    }

    public void setWalkStartedFromVertex(boolean started) {
      walkStartedFromVertex = started;
    }

    public void testFinished() {
        if (!tested) {
            if (!dieIfCalled) {
                TestCase.assertTrue(false);
            }
        } else {
            if (dieIfCalled) {
                TestCase.assertTrue(false);
            }
        }
        tested = false;
        dieIfCalled = false;
    }

    public void dieIfCalled() {
        dieIfCalled = true;
    }

    @Override
    public void forwardWalkTo(int walk, int nextVertex, boolean trackBit) {
        TestCase.assertEquals(false, expectReset);
        TestCase.assertEquals(expectedWalk, walk);
        TestCase.assertEquals(expectedTrackBit, trackBit);
        TestCase.assertEquals(expectedNextVertex, nextVertex);
        tested = true;
    }

    @Override
    public void resetWalk(int walk, boolean trackBit) {
        TestCase.assertEquals(true, expectReset);
        TestCase.assertEquals(expectedWalk, walk);
        TestCase.assertEquals(expectedTrackBit, trackBit);
        tested = true;
    }

    @Override
    public int getIteration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VertexIdTranslate getVertexIdTranslate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sourceIndex() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getTrackBit(int walk) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWalkStartedFromVertex(int walk) {
        return walkStartedFromVertex;
    }
}

