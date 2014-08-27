package edu.cmu.ml.rtw.pra.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Lists;

public class GraphCreatorTest extends TestCase {
    private String embeddings1 = "embeddings1";
    private String embeddings2 = "embeddings2";

    public void testGetSvoPrefixes() throws IOException {
        List<RelationSet> relationSets = Lists.newArrayList();
        String file = "embeddings file\t" + embeddings1 + "\n";
        relationSets.add(RelationSet.fromReader(new BufferedReader(new StringReader(file))));
        file = "embeddings file\t" + embeddings1 + "\n";
        relationSets.add(RelationSet.fromReader(new BufferedReader(new StringReader(file))));
        file = "embeddings file\t" + embeddings2 + "\n";
        relationSets.add(RelationSet.fromReader(new BufferedReader(new StringReader(file))));

        Map<RelationSet, String> prefixes = new GraphCreator(relationSets, "").getSvoPrefixes();
        assertEquals("1-", prefixes.get(relationSets.get(0)));
        assertEquals("1-", prefixes.get(relationSets.get(1)));
        assertEquals("2-", prefixes.get(relationSets.get(2)));

        // No need for a prefix map if there isn't any ambiguity about where the embeddings came
        // from.
        relationSets.remove(2);
        prefixes = new GraphCreator(relationSets, "").getSvoPrefixes();
        assertEquals(null, prefixes);
    }
}
