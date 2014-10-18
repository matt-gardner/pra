package edu.cmu.ml.rtw.users.matt.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class MapUtil {

  public static <K1, K2, V> void addValueToTwoKeyList(Map<K1, Map<K2, List<V>>> map,
                                                      K1 key1,
                                                      K2 key2,
                                                      V value) {
    Map<K2, List<V>> key1Map = MapUtil.getWithDefaultAndAdd(map, key1, new HashMap<K2, List<V>>());
    MapUtil.addValueToKeyList(key1Map, key2, value);
  }

  public static <K> void incrementCount(Map<K, Integer> map, K key) {
    incrementCount(map, key, 1);
  }

  public static <K> void incrementCount(Map<K, Integer> map, K key, int count) {
    Integer currentCount = map.get(key);
    if (currentCount == null) {
      map.put(key, count);
    } else {
      map.put(key, count + currentCount);
    }
  }

  public static <K, V extends Comparable<V>> List<K> getKeysSortedByValue(Map<K, V> map) {
    return getKeysSortedByValue(map, false);
  }

  public static <K, V extends Comparable<V>> List<K> getKeysSortedByValue(Map<K, V> map,
                                                                          boolean reverse) {
    List<K> keys = Lists.newArrayList();
    for (Map.Entry<K, V> entry : sortByValue(map, reverse)) {
      keys.add(entry.getKey());
    }
    return keys;
  }

  public static <K, V extends Comparable<V>> List<Map.Entry<K, V>> sortByValue(Map<K, V> map) {
    return sortByValue(map, false);
  }

  public static <K, V extends Comparable<V>> List<Map.Entry<K, V>> sortByValue(Map<K, V> map,
                                                                               boolean reverse) {
    List<Map.Entry<K, V>> list = Lists.newArrayList(map.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
      public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
        return -o1.getValue().compareTo(o2.getValue());
      }
    });
    return list;
  }

  public static <K extends Comparable<K>, V> List<Map.Entry<K, V>> sortByKey(Map<K, V> map) {
    return sortByKey(map, false);
  }

  public static <K extends Comparable<K>, V> List<Map.Entry<K, V>> sortByKey(Map<K, V> map,
                                                                             boolean reverse) {
    List<Map.Entry<K, V>> list = Lists.newArrayList(map.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
      public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
        return -o1.getKey().compareTo(o2.getKey());
      }
    });
    return list;
  }

  public static <K, V> V getWithDefault(Map<K, V> map, K key, V defaultValue) {
    V value = map.get(key);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  public static <K, V> V getWithDefaultAndAdd(Map<K, V> map, K key, V defaultValue) {
    V value = map.get(key);
    if (value == null) {
      map.put(key, defaultValue);
      return defaultValue;
    }
    return value;
  }

  public static <K, V> V getWithDefaultAllowNullMap(Map<K, V> map, K key, V defaultValue) {
    if (map == null) {
      return defaultValue;
    }
    return getWithDefault(map, key, defaultValue);
  }

  public static <K, V extends Comparable<V>> List<K> getTopKeys(Map<K, V> keyCounts, int numToKeep) {
    List<Map.Entry<K, V>> list = Lists.newArrayList(keyCounts.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
      public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
        return -o1.getValue().compareTo(o2.getValue());
      }
    });
    List<K> topKeys = Lists.newArrayList();
    for (int i=0; i<numToKeep; i++) {
      if (i >= list.size()) break;
      topKeys.add(list.get(i).getKey());
    }
    return topKeys;
  }

  /**
   * Adds value to the list corresponding to key in the map.  If the key does not already have a
   * list, we instantiate an ArrayList.
   */
  public static <K, V> void addValueToKeyList(Map<K, List<V>> map, K key, V value) {
    List<V> list = map.get(key);
    if (list == null) {
      list = Lists.newArrayList();
      map.put(key, list);
    }
    list.add(value);
  }

  /**
   * Adds value to the set corresponding to key in the map.  If the key does not already have a
   * set, we instantiate a HashMap.
   */
  public static <K, V> void addValueToKeySet(Map<K, Set<V>> map, K key, V value) {
    Set<V> set = map.get(key);
    if (set == null) {
      set = Sets.newHashSet();
      map.put(key, set);
    }
    set.add(value);
  }
}
