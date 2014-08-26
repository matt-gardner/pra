/**
 * 
 */
package edu.cmu.ml.rtw.util;

import java.util.Comparator;

/**
 * @author acarlson, mwgreave
 * 
 * This class provides a comparator that can be used to sort a collection of
 * Pair objects by either the left or right elements in the Pairs.
 * 
 * The types used in instantiating your PairComparator must match the types in
 * the Pairs in the collection. Furthermore, both types must implement the
 * Comparable interface.
 * 
 * Example usage:
 * 
 * List<Pair<NGram, Double> > myList = ...;
 * 
 * To sort by the right elements:
 * 
 * Comparator<Pair<NGram, Double>> right_comparator = new PairComparator<NGram,
 * Double>(PairComparator.Side.RIGHT);
 * 
 * Collections.sort(myList, right_comparator);
 * 
 * To sort by the left elements:
 * 
 * Comparator<Pair<NGram, Double>> left_comparator = new PairComparator<NGram,
 * Double>(PairComparator.Side.LEFT);
 * 
 * Collections.sort(myList, left_comparator);
 * 
 * (mwgreave)
 * To sort in descending order by the left or right value 
 * use NEGLEFT and NEGRIGHT, respectively.
 * 
 * e.g.
 * 
 * Comparator<Pair<NGram, Double>> right_comparator = new PairComparator<NGram,
 * Double>(PairComparator.Side.NEGRIGHT);
 * 		will compare on the right element and allow one to sort a list of Pairs
 * 		with this comparator into descending order.
 */

public class PairComparator<A extends Comparable<? super A>, B extends Comparable<? super B>>
        implements Comparator<Pair<A, B>> {
	
    public enum Side {
        LEFT,     // order by LEFT item; for ascending order
        RIGHT,    // order by RIGHT item; for ascending order
        
        NEGLEFT,  // order by LEFT item; for descending order
        NEGRIGHT  // order by RIGHT item; for descending order
    }
    // Determines *how* the comparator compares two items.
    Side comparisonSide;

    public PairComparator(Side c) {
        comparisonSide = c;
    }

    public int compare(Pair<A, B> p1, Pair<A, B> p2) {
        if (comparisonSide == Side.LEFT)
            return p1.getLeft().compareTo(p2.getLeft());
        else if(comparisonSide == Side.RIGHT)
            return p1.getRight().compareTo(p2.getRight());
        else if(comparisonSide == Side.NEGLEFT)
        	return -(p1.getLeft().compareTo(p2.getLeft()));
        else // NEGRIGHT
        	return -(p1.getRight().compareTo(p2.getRight()));
    }
}
