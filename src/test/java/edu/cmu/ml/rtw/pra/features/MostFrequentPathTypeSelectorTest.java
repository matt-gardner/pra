package edu.cmu.ml.rtw.pra.features;

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.users.matt.util.TestUtil;

public class MostFrequentPathTypeSelectorTest extends TestCase {
    private MostFrequentPathTypeSelector selector = new MostFrequentPathTypeSelector();

    public void testSelectPaths() {
        PathTypeFactory factory = new BasicPathTypeFactory();
        PathType type1 = factory.fromString("-1-");
        PathType type2 = factory.fromString("-2-");
        Map<PathType, Integer> counts = Maps.newHashMap();
        counts.put(type1, 10);
        counts.put(type2, 100);
        List<PathType> selected = selector.selectPathTypes(counts, 1);
        assertEquals(1, selected.size());
        TestUtil.assertCount(selected, type2, 1);
    }
}
