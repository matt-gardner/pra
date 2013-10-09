package edu.cmu.ml.rtw.util;

/**
 * Variations on sorting.
 *
 * Collections.sort is fine for many purposes, but sometimes the happy world of OO design that Java
 * imagines comes into contact with the hard, cold reality of needing to work with plain arrays of
 * primitive types, or even parallel arrays thereof.  These sorts of things often come into
 * happenstance due to speed and size concerns.  The methods in this class are for those various and
 * sometimes oddball cases.
 */
public class Sort {

    /**
     * Take two parallel arrays and reorder their elements so that the keys array is a heap in
     * accordance with KEY.compareTo.
     *
     * The main purpose of this is to set up two parallel arrays to be sorted by {@link heapsort}.
     *
     * N.B. Java does not allow KEY or VALUE to be a primitive type.  So if you need one or both to
     * be primitive types, you'll just have to cut and paste and search and replace.  That's why we
     * have a whole "Sort" class that we can clutter up.
     */
    public static <KEY extends Comparable<? super KEY>, VALUE> void heapify(KEY[] keys, VALUE[] values) {
        int len = keys.length;
        if (values.length != len)
            throw new RuntimeException("Arrays are different lengths and therefore not parallel");
        int end = len - 1;                 // Set start to last parent node
        int i = end / 2;
        while (i >= 0) {                   // And scan backward to treat all parents

            // "sift down" this parent into the proper place in the heap
            int parent = i;
            int child = parent * 2 + 1;    // (Greatest) child of parent
            boolean stop = false;
            while (!stop && child <= end) {
                if (child < end && keys[child].compareTo(keys[child+1]) < 0) child++;
                if (keys[parent].compareTo(keys[child]) < 0) { // Current with greatest child if needed to keep heap order
                    KEY ktmp = keys[child];
                    keys[child] = keys[parent];
                    keys[parent] = ktmp;
                    VALUE vtmp = values[child];
                    values[child] = values[parent];
                    values[parent] = vtmp;

                    parent = child;
                    child = parent * 2 + 1;
                } else {
                    stop = true;           // No longer out of heaping order
                }
            }
            i--;
        }
    }

    /**
     * Heapsort two parallel arrays according to the keys array and its compareTo method
     *
     * The keys array must already be in heap order.  {@link heapify} may be used to ensure this.
     *
     * N.B. Java does not allow KEY or VALUE to be a primitive type.  So if you need one or both to
     * be primitive types, you'll just have to cut and paste and search and replace.  That's why we
     * have a whole "Sort" class that we can clutter up.
     */
    public static <KEY extends Comparable<? super KEY>, VALUE> void heapsort(KEY[] keys, VALUE[] values) {
        int len = keys.length;
        if (values.length != len)
            throw new RuntimeException("Arrays are different lengths and therefore not parallel");
        int end = len - 1;
        while (end > 0) {
            // swap the root out to the end as part of the sorted portion and reduce the heap size.
            KEY ktmp = keys[end];
            keys[end] = keys[0];
            keys[0] = ktmp;
            VALUE vtmp = values[end];
            values[end] = values[0];
            values[0] = vtmp;

            end--;

            // "sift down" the new root into the proper place in the heap
            int i = 0;                     // Current node
            int child= 1;                  // (Greatest) child of current node
            while (child <= end) {
                if (child < end && keys[child].compareTo(keys[child+1]) < 0) child++;
                if (keys[i].compareTo(keys[child]) < 0) {  // Current with greatest child if needed to keep heap order
                    ktmp = keys[child];
                    keys[child] = keys[i];
                    keys[i] = ktmp;
                    vtmp = values[child];
                    values[child] = values[i];
                    values[i] = vtmp;

                    i = child;
                    child = i * 2 + 1;
                } else {
                    break;                   // No longer out of heaping order
                }
            }
        }
    }

    /**
     * Special case of heapify for int[] / double[]
     */
    public static void heapify(int[] keys, double[] values) {
        int len = keys.length;
        if (values.length != len)
            throw new RuntimeException("Arrays are different lengths and therefore not parallel");
        int end = len - 1;                 // Set start to last parent node
        int i = end / 2;
        while (i >= 0) {                   // And scan backward to treat all parents

            // "sift down" this parent into the proper place in the heap
            int parent = i;
            int child = parent * 2 + 1;    // (Greatest) child of parent
            boolean stop = false;
            while (!stop && child <= end) {
                if (child < end && keys[child] < keys[child+1]) child++;
                if (keys[parent] < keys[child]) { // Current with greatest child if needed to keep heap order
                    int ktmp = keys[child];
                    keys[child] = keys[parent];
                    keys[parent] = ktmp;
                    double vtmp = values[child];
                    values[child] = values[parent];
                    values[parent] = vtmp;

                    parent = child;
                    child = parent * 2 + 1;
                } else {
                    stop = true;           // No longer out of heaping order
                }
            }
            i--;
        }
    }

    /**
     * Special case of heapify for int[] / double[]
     */
    public static void heapsort(int[] keys, double[] values) {
        int len = keys.length;
        if (values.length != len)
            throw new RuntimeException("Arrays are different lengths and therefore not parallel");
        int end = len - 1;
        while (end > 0) {
            // swap the root out to the end as part of the sorted portion and reduce the heap size.
            int ktmp = keys[end];
            keys[end] = keys[0];
            keys[0] = ktmp;
            double vtmp = values[end];
            values[end] = values[0];
            values[0] = vtmp;

            end--;

            // "sift down" the new root into the proper place in the heap
            int i = 0;                     // Current node
            int child= 1;                  // (Greatest) child of current node
            while (child <= end) {
                if (child < end && keys[child] < keys[child+1]) child++;
                if (keys[i] < keys[child]) {  // Current with greatest child if needed to keep heap order
                    ktmp = keys[child];
                    keys[child] = keys[i];
                    keys[i] = ktmp;
                    vtmp = values[child];
                    values[child] = values[i];
                    values[i] = vtmp;

                    i = child;
                    child = i * 2 + 1;
                } else {
                    break;                   // No longer out of heaping order
                }
            }
        }
    }

    /**
     * Special case of heapify for double[] / int[]
     */
    public static void heapify(double[] keys, int[] values) {
        int len = keys.length;
        if (values.length != len)
            throw new RuntimeException("Arrays are different lengths and therefore not parallel");
        int end = len - 1;                 // Set start to last parent node
        int i = end / 2;
        while (i >= 0) {                   // And scan backward to treat all parents

            // "sift down" this parent into the proper place in the heap
            int parent = i;
            int child = parent * 2 + 1;    // (Greatest) child of parent
            boolean stop = false;
            while (!stop && child <= end) {
                if (child < end && keys[child] < keys[child+1]) child++;
                if (keys[parent] < keys[child]) { // Current with greatest child if needed to keep heap order
                    double ktmp = keys[child];
                    keys[child] = keys[parent];
                    keys[parent] = ktmp;
                    int vtmp = values[child];
                    values[child] = values[parent];
                    values[parent] = vtmp;

                    parent = child;
                    child = parent * 2 + 1;
                } else {
                    stop = true;           // No longer out of heaping order
                }
            }
            i--;
        }
    }

    /**
     * Special case of heapify for double[] / int[]
     */
    public static void heapsort(double[] keys, int[] values) {
        int len = keys.length;
        if (values.length != len)
            throw new RuntimeException("Arrays are different lengths and therefore not parallel");
        int end = len - 1;
        while (end > 0) {
            // swap the root out to the end as part of the sorted portion and reduce the heap size.
            double ktmp = keys[end];
            keys[end] = keys[0];
            keys[0] = ktmp;
            int vtmp = values[end];
            values[end] = values[0];
            values[0] = vtmp;

            end--;

            // "sift down" the new root into the proper place in the heap
            int i = 0;                     // Current node
            int child= 1;                  // (Greatest) child of current node
            while (child <= end) {
                if (child < end && keys[child] < keys[child+1]) child++;
                if (keys[i] < keys[child]) {  // Current with greatest child if needed to keep heap order
                    ktmp = keys[child];
                    keys[child] = keys[i];
                    keys[i] = ktmp;
                    vtmp = values[child];
                    values[child] = values[i];
                    values[i] = vtmp;

                    i = child;
                    child = i * 2 + 1;
                } else {
                    break;                   // No longer out of heaping order
                }
            }
        }
    }
}
