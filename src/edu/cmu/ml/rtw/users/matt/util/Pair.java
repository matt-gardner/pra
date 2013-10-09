/*
 * Pair.java
 *
 * Created on February 12, 2007, 4:14 PM
 */

package edu.cmu.ml.rtw.util;

import java.io.Serializable;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a pair of values. We use it a lot for pairs of feature IDs and
 * values.
 * 
 * @author acarlson -- copied from
 *         http://forum.java.sun.com/thread.jspa?threadID=5132045
 */
public class Pair<L, R> implements Serializable {
		private static final long serialVersionUID = 1L;
	
    protected final L left;
    protected final R right;

    /**
     * Returns the right entry of the pair.
     * 
     * @return The right value
     */
    public R getRight() {
        return right;
    }

    /**
     * Returns the left entry of the pair.
     * 
     * @return The left value
     */
    public L getLeft() {
        return left;
    }

    /**
     * Constructor for a pair of values.
     * 
     * @param left
     *                The left value
     * @param right
     *                The right value
     */
    public Pair(final L left, final R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Factory method that creates an object that is a Pair of values.
     * 
     * @param left
     *                The left value
     * @param right
     *                The right value
     * @return The Pair object containing the specified values.
     */
    public static <A, B> Pair<A, B> create(A left, B right) {
        return new Pair<A, B>(left, right);
    }

    /**
     * 
     * @param o
     * @return
     */
    public final boolean equals(Object o) {
        if (!(o instanceof Pair))
            return false;

        final Pair<?, ?> other = (Pair) o;
        return equal(getLeft(), other.getLeft()) && equal(getRight(), other.getRight());
    }

    /**
     * 
     * @param o1
     * @param o2
     * @return
     */
    public static final boolean equal(Object o1, Object o2) {
        if (o1 == null) {
            return o2 == null;
        }
        return o1.equals(o2);
    }

    /**
     * Returns a hash code for the object.
     * 
     * @return
     */
    public int hashCode() {
        int hLeft = getLeft() == null ? 0 : getLeft().hashCode();
        int hRight = getRight() == null ? 0 : getRight().hashCode();

        return hLeft + (57 * hRight);
    }

    public String toString() {
        String right = (getRight() == null) ? null : getRight().toString();
        String left = (getLeft() == null) ? null : getLeft().toString();
        return String.format("(%s,%s)", left, right);

    }
    
    
    /**
     * Allows one to do this:
     * 
     * TreeSet<Pair.AscendLeftComparable<Double, String>> listOfPairsToSort;
     * listOfPairsToSort = new TreeSet<Pair.AscendLeftComparable<Double, String>>();
     * ...
     * ...
     * ...add some stuff to listOfPairsToSort...
     * ...
     * ...
     * ---can now traverse things in the set in sorted order, based upon the LEFT element of each pair---
     * 
     * 
     * @author Malcolm Greaves
     * 
     * @param <L> Left
     * @param <R> Right
     */
    public static class AscendLeftComparable<L extends Comparable<? super L>, R>
												extends Pair<L,R> implements Comparable<AscendLeftComparable<L,R>> {
    	private static final long serialVersionUID = 1L;
    	public AscendLeftComparable(L left, R right){
    		super(left,right);
    	}
    	@Override
    	public int compareTo(AscendLeftComparable<L, R> other){
    		return this.left.compareTo(other.left);
    	}
    }
    public static class DescendLeftComparable<L extends Comparable<? super L>, R>
												extends Pair<L,R> implements Comparable<DescendLeftComparable<L,R>> {
    	private static final long serialVersionUID = 1L;
    	public DescendLeftComparable(L left, R right){
    		super(left,right);
    	}
    	@Override
    	public int compareTo(DescendLeftComparable<L, R> other){
    		return -this.left.compareTo(other.left);
    	}
    }
    
    
    /**
     * Allows one to do this:
     * 
     * TreeSet<Pair.DescendRightComparable<String,Double>> listOfPairsToSort;
     * listOfPairsToSort = new TreeSet<Pair.DescendRightComparable<String,Double>>();
     * ...
     * ...
     * ...add some stuff to listOfPairsToSort...
     * ...
     * ...
     * ---can now traverse things in the set in sorted order, based upon the RIGHT element of each pair---
     * 
     * 
     * @author Malcolm Greaves
     * 
     * @param <L> Left
     * @param <R> Right
     */
    public static class AscendRightComparable<L, R extends Comparable<? super R>>
    										extends Pair<L,R> implements Comparable<AscendRightComparable<L,R>> {
			private static final long serialVersionUID = 1L;
    	public AscendRightComparable(L left, R right){
    		super(left,right);
    	}
			@Override
			public int compareTo(AscendRightComparable<L, R> other){
				return this.right.compareTo(other.right);
			}
		}
    public static class DescendRightComparable<L, R extends Comparable<? super R>>
    										extends Pair<L,R> implements Comparable<DescendRightComparable<L,R>> {
			private static final long serialVersionUID = 1L;
    	public DescendRightComparable(L left, R right){
    		super(left,right);
    	}
			@Override
			public int compareTo(DescendRightComparable<L, R> other){
				return -this.right.compareTo(other.right);
			}
		}
}