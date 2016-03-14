package edu.cmu.ml.rtw.pra.features;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.pra.features.VectorPathTypeFactory.VectorPathType;
import edu.cmu.ml.rtw.pra.graphs.GraphInMemory;
import edu.cmu.ml.rtw.pra.graphs.Node;
import com.mattg.util.FileUtil;
import com.mattg.util.MutableConcurrentDictionary;
import com.mattg.util.Pair;
import com.mattg.util.TestUtil;
import com.mattg.util.Vector;

public class VectorClusteringPathTypeSelectorTest extends TestCase {
    private MutableConcurrentDictionary edgeDict = new MutableConcurrentDictionary(false, new FileUtil());
    private GraphInMemory graph = new GraphInMemory(new Node[0], null, edgeDict);
    private Map<Integer, Vector> embeddings = Maps.newHashMap();
    private VectorPathTypeFactory factory = new VectorPathTypeFactory(graph, embeddings, 1, .5);
    private VectorClusteringPathTypeSelector selector =
            new VectorClusteringPathTypeSelector(factory, .75);

    private String alias = "alias";
    private String surface1 = "surface1";
    private String surface2 = "surface2";
    private String surface3 = "surface3";
    private String cluster1 = surface1 + VectorClusteringPathTypeSelector.CLUSTER_SEPARATOR + surface2;
    private String cluster2 = surface2 + VectorClusteringPathTypeSelector.CLUSTER_SEPARATOR + surface1;
    private int aliasIndex;
    private int surface1Index;
    private int surface2Index;
    private int surface3Index;
    private int cluster1Index;
    private int cluster2Index;
    private PathType type1;
    private PathType type2;
    private PathType type3;
    private PathType aliasType1;
    private PathType aliasType2;
    private PathType aliasType3;
    private Map<PathType, Integer> counts = Maps.newHashMap();

    private Vector surface1Vector = new Vector(new double[]{10,10,0});
    private Vector surface2Vector = new Vector(new double[]{10,11,0});
    private Vector surface3Vector = new Vector(new double[]{0,0,1});

    @Override
    public void setUp() {
        aliasIndex = edgeDict.getIndex(alias);
        cluster1Index = edgeDict.getIndex(cluster1);
        cluster2Index = edgeDict.getIndex(cluster2);
        surface1Index = edgeDict.getIndex(surface1);
        surface1Vector.normalize();
        embeddings.put(surface1Index, surface1Vector);
        surface2Index = edgeDict.getIndex(surface2);
        surface2Vector.normalize();
        embeddings.put(surface2Index, surface2Vector);
        surface3Index = edgeDict.getIndex(surface3);
        surface3Vector.normalize();
        embeddings.put(surface3Index, surface3Vector);
        factory.initializeEmbeddings();
        type1 = factory.fromString("-" + surface1Index + "-");
        type2 = factory.fromString("-" + surface2Index + "-");
        type3 = factory.fromString("-" + surface3Index + "-");
        aliasType1 = factory.fromString("-" + aliasIndex + "-" + surface1Index + "-");
        aliasType2 = factory.fromString("-" + aliasIndex + "-" + surface2Index + "-");
        aliasType3 = factory.fromString("-" + aliasIndex + "-" + surface3Index + "-");
        counts.put(type1, 10);
        counts.put(type2, 10);
        counts.put(type3, 10);
        counts.put(aliasType1, 100);
        counts.put(aliasType2, 100);
        counts.put(aliasType3, 1);
    }

    public void testSelectPaths() {
        List<PathType> selected = selector.selectPathTypes(counts, 2);
        for (PathType type : selected) {
            System.out.println("SELECTED: " + type);
        }
        assertEquals(2, selected.size());
        // These should be a combination of aliasType1 and aliasType2, and a combination of type1
        // and type2.  Testing that is a little tricky...
        PathType clusterType1 = factory.fromString("-" + cluster1Index + "-");
        PathType clusterType2 = factory.fromString("-" + cluster2Index + "-");
        // We don't know which order the strings were clustered in, but one of these had better be
        // found.
        if (selected.contains(clusterType1)) {
            TestUtil.assertCount(selected, clusterType1, 1);
            TestUtil.assertCount(selected, clusterType2, 0);
        } else {
            TestUtil.assertCount(selected, clusterType1, 0);
            TestUtil.assertCount(selected, clusterType2, 1);
        }
        clusterType1 = factory.fromString("-" + aliasIndex + "-" + cluster1Index + "-");
        clusterType2 = factory.fromString("-" + aliasIndex + "-" + cluster2Index + "-");
        // Again, we should have exactly one of these two.
        if (selected.contains(clusterType1)) {
            TestUtil.assertCount(selected, clusterType1, 1);
            TestUtil.assertCount(selected, clusterType2, 0);
        } else {
            TestUtil.assertCount(selected, clusterType1, 0);
            TestUtil.assertCount(selected, clusterType2, 1);
        }
    }

    public void testGetSignature() {
        String signature = "-v-";
        assertEquals(signature, selector.getSignature((VectorPathType) type1));
        assertEquals(signature, selector.getSignature((VectorPathType) type2));
        assertEquals(signature, selector.getSignature((VectorPathType) type3));
        signature = "-" + aliasIndex + "-v-";
        assertEquals(signature, selector.getSignature((VectorPathType) aliasType1));
        assertEquals(signature, selector.getSignature((VectorPathType) aliasType2));
        assertEquals(signature, selector.getSignature((VectorPathType) aliasType3));
        signature = "-_v-";
        PathType reverseType = factory.fromString("-_" + surface1Index + "-");
        assertEquals(signature, selector.getSignature((VectorPathType) reverseType));
    }

    public void testGroupBySignature() {
        Collection<List<Pair<PathType, Integer>>> grouped = selector.groupBySignature(counts);
        assertEquals(2, grouped.size());
        List<Pair<PathType, Integer>> group1 = Lists.newArrayList();
        group1.add(new Pair<PathType, Integer>(type1, counts.get(type1)));
        group1.add(new Pair<PathType, Integer>(type2, counts.get(type2)));
        group1.add(new Pair<PathType, Integer>(type3, counts.get(type3)));
        List<Pair<PathType, Integer>> group2 = Lists.newArrayList();
        group2.add(new Pair<PathType, Integer>(aliasType1, counts.get(aliasType1)));
        group2.add(new Pair<PathType, Integer>(aliasType2, counts.get(aliasType2)));
        group2.add(new Pair<PathType, Integer>(aliasType3, counts.get(aliasType3)));
        for (List<Pair<PathType, Integer>> group : grouped) {
            assertEquals(3, group.size());
            if (group.contains(group1.get(0))) {
                for (Pair<PathType, Integer> entry : group1) {
                    TestUtil.assertCount(group, entry, 1);
                }
            } else {
                for (Pair<PathType, Integer> entry : group2) {
                    TestUtil.assertCount(group, entry, 1);
                }
            }
        }
    }

    public void testGetVectorFromPathType() {
        PathType twoVectors = factory.fromString("-" + surface1Index + "-" + surface2Index + "-");
        Vector expected = surface1Vector.concatenate(surface2Vector);
        assertEquals(expected, selector.getVectorFromPathType(twoVectors));
    }
}
