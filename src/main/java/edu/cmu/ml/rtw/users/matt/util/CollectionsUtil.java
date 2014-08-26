package edu.cmu.ml.rtw.users.matt.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.ml.rtw.util.Pair;

public class CollectionsUtil {
    public static <L, R> List<Pair<L, R>> zipLists(List<L> listOne, List<R> listTwo) {
        List<Pair<L, R>> zipped = new ArrayList<Pair<L, R>>();
        for (int i = 0; i < listOne.size(); i++) {
            zipped.add(new Pair<L, R>(listOne.get(i), listTwo.get(i)));
        }
        return zipped;
    }

    public static <L, R> List<L> unzipLeft(List<Pair<L, R>> list) {
        List<L> unzipped = new ArrayList<L>();
        for (Pair<L, R> pair : list) {
            unzipped.add(pair.getLeft());
        }
        return unzipped;
    }

    public static <L, R> List<R> unzipRight(List<Pair<L, R>> list) {
        List<R> unzipped = new ArrayList<R>();
        for (Pair<L, R> pair : list) {
            unzipped.add(pair.getRight());
        }
        return unzipped;
    }

    /**
     * Allows values to be null, and creates a map where every key maps to an empty set in that
     * case.
     */
    public static <K, V> Map<K, Set<V>> groupByKey(List<K> keys, List<V> values) {
        if (values == null) {
            return groupByKeyEmpty(keys);
        } else {
            return groupByKey(zipLists(keys, values));
        }
    }

    public static <K, V> Map<K, Set<V>> groupByKey(List<Pair<K, V>> pairs) {
        Map<K, Set<V>> map = new HashMap<K, Set<V>>();
        for (Pair<K, V> pair : pairs) {
            Set<V> keyValues = map.get(pair.getLeft());
            if (keyValues == null) {
                keyValues = new HashSet<V>();
                map.put(pair.getLeft(), keyValues);
            }
            keyValues.add(pair.getRight());
        }
        return map;
    }

    public static <K, V> Map<K, Set<V>> groupByKeyEmpty(List<K> keys) {
        Map<K, Set<V>> map = new HashMap<K, Set<V>>();
        for (K key : keys) {
            if (!map.containsKey(key)) {
                map.put(key, new HashSet<V>());
            }
        }
        return map;
    }

    public static <K, V> Map<K, Set<V>> combineMapSets(Map<K, Set<V>> map1, Map<K, Set<V>> map2) {
        Map<K, Set<V>> combined = new HashMap<K, Set<V>>(map1);
        for (Map.Entry<K, Set<V>> entry : map2.entrySet()) {
            Set<V> values = combined.get(entry.getKey());
            if (values == null) {
                values = new HashSet<V>();
                combined.put(entry.getKey(), values);
            }
            for (V value : entry.getValue()) {
                values.add(value);
            }
        }
        return combined;
    }
}
