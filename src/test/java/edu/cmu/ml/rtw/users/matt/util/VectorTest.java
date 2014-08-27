package edu.cmu.ml.rtw.users.matt.util;

import junit.framework.TestCase;

public class VectorTest extends TestCase {

    public void testNormalize() {
        Vector v = new Vector(new double[]{1,4,6,10});
        assertFalse(v.norm() == 1.0);
        v.normalize();
        assertEquals(1.0, v.norm());
    }

    public void testDotProduct() {
        Vector v1 = new Vector(new double[]{1,4,6});
        Vector v2 = new Vector(new double[]{1,-4,6});
        assertEquals(21.0, v1.dotProduct(v2));
    }
}
