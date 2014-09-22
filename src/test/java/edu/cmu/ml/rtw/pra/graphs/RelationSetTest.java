package edu.cmu.ml.rtw.pra.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.FakeFileWriter;
import edu.cmu.ml.rtw.users.matt.util.TestUtil;
import edu.cmu.ml.rtw.users.matt.util.TestUtil.Function;
import edu.cmu.ml.rtw.util.Pair;

public class RelationSetTest extends TestCase {
    private String relationFile = "test relation file";
    private String relationPrefix = "test relation prefix";
    private boolean isKb = true;
    private String aliasFile = "test alias file";
    private boolean aliasesOnly = false;
    private String aliasRelation = "alias";
    private String aliasFileFormat = "nell";
    private String embeddingsFile = "test embeddings file";
    private String prefix = "prefix-";
    private boolean keepOriginalEdges = false;
    private String alias1 = "alias1";
    private String alias2 = "alias2";
    private String concept1 = "concept1";
    private String concept2 = "concept2";
    private String embedded1 = "embedded1";
    private String embedded2 = "embedded2";
    private String np1 = "np1";
    private String np2 = "np2";
    private String relation1 = "relation1";
    private String relation2 = "relation2";
    private String verb1 = "verb1";
    private String verb2 = "verb2";
    private Dictionary nodeDict = new Dictionary();
    private Dictionary edgeDict = new Dictionary();
    private int concept1Index = nodeDict.getIndex(concept1);
    private int concept2Index = nodeDict.getIndex(concept2);
    private int np1Index = nodeDict.getIndex(np1);
    private int np2Index = nodeDict.getIndex(np2);
    private int verb1Index = edgeDict.getIndex(prefix + verb1);
    private int verb2Index = edgeDict.getIndex(prefix + verb2);
    private int embedded1Index = edgeDict.getIndex(prefix + embedded1);
    private int embedded2Index = edgeDict.getIndex(prefix + embedded2);
    private int aliasRelationIndex = edgeDict.getIndex(aliasRelation);
    private int relation1Index = edgeDict.getIndex(prefix + relation1);
    private int relation2Index = edgeDict.getIndex(prefix + relation2);
    private int relationNoPrefix1Index = edgeDict.getIndex(relation1);

    public void testFromReader() throws IOException {
        String relationSetFile = "";
        relationSetFile += "relation file\t" + relationFile + "\n";
        relationSetFile += "relation prefix\t" + relationPrefix + "\n";
        relationSetFile += "is kb\t" + isKb + "\n";
        relationSetFile += "alias file\t" + aliasFile + "\n";
        relationSetFile += "aliases only\t" + aliasesOnly + "\n";
        relationSetFile += "alias relation\t" + aliasRelation + "\n";
        relationSetFile += "alias file format\t" + aliasFileFormat + "\n";
        relationSetFile += "embeddings file\t" + embeddingsFile + "\n";
        relationSetFile += "keep original edges\t" + keepOriginalEdges + "\n";

        BufferedReader reader = new BufferedReader(new StringReader(relationSetFile));
        RelationSet relationSet = RelationSet.fromReader(reader);
        assertEquals(relationFile, relationSet.getRelationFile());
        assertEquals(relationPrefix, relationSet.getRelationPrefix());
        assertEquals(isKb, relationSet.getIsKb());
        assertEquals(aliasFile, relationSet.getAliasFile());
        assertEquals(aliasesOnly, relationSet.getAliasesOnly());
        assertEquals(aliasRelation, relationSet.getAliasRelation());
        assertEquals(embeddingsFile, relationSet.getEmbeddingsFile());
        assertEquals(keepOriginalEdges, relationSet.getKeepOriginalEdges());
    }

    public void testGetAliases() throws IOException {
        // First we test a nell format.
        String relationSetFile = "alias file format\tnell\n";
        BufferedReader reader = new BufferedReader(new StringReader(relationSetFile));
        RelationSet relationSet = RelationSet.fromReader(reader);

        String aliasFile = "";
        aliasFile += concept1 + "\t" + np1 + "\n";
        aliasFile += concept1 + "\t" + np2 + "\n";
        aliasFile += concept2 + "\t" + np2 + "\n";

        reader = new BufferedReader(new StringReader(aliasFile));
        Map<String, List<String>> aliases = relationSet.getAliasesFromReader(reader);
        TestUtil.assertCount(aliases.get(np1), concept1, 1);
        TestUtil.assertCount(aliases.get(np2), concept1, 1);
        TestUtil.assertCount(aliases.get(np2), concept2, 1);

        // Then we test a freebase format.
        relationSetFile = "alias file format\tfreebase\n";
        reader = new BufferedReader(new StringReader(relationSetFile));
        relationSet = RelationSet.fromReader(reader);

        aliasFile = "";
        // Good rows that should be seen in the resultant map.
        aliasFile += concept1 + "\t/type/object/name\t/lang/en\t" + alias1 + "\n";
        aliasFile += concept2 + "\t/common/topic/alias\t/lang/en\t" + alias1 + "\n";
        aliasFile += concept2 + "\tdoesn't really matter\t/lang/en\t" + alias2 + "\n";
        // Bad rows that should be filtered out.
        aliasFile += concept1 + "\t/type/object/key\t/wikipedia/en\twiki1\n";
        aliasFile += concept1 + "\t/type/object/name\t/lang/en\t" + alias1 + "\textra column\n";
        aliasFile += concept1 + "\t/type/object/name\t/lang/en\n";

        reader = new BufferedReader(new StringReader(aliasFile));
        aliases = relationSet.getAliasesFromReader(reader);
        TestUtil.assertCount(aliases.get(alias1), concept1, 1);
        TestUtil.assertCount(aliases.get(alias1), concept2, 1);
        TestUtil.assertCount(aliases.get(alias2), concept2, 1);

        // And now we make sure we get an error with an unrecognized format.
        TestUtil.expectError(RuntimeException.class, new Function() {
            @Override
            public void call() {
                try {
                    String relationSetFile = "alias file format\tunknown\n";
                    BufferedReader reader = new BufferedReader(new StringReader(relationSetFile));
                    RelationSet relationSet = RelationSet.fromReader(reader);

                    String aliasFile = "";

                    reader = new BufferedReader(new StringReader(aliasFile));
                    Map<String, List<String>> aliases = relationSet.getAliasesFromReader(reader);
                } catch (IOException e) {
                }
            }
        });
    }

    public void testGetEmbeddedRelations() throws IOException {
        String relationSetFile = "is kb\tfalse\n";
        RelationSet relationSet =
                RelationSet.fromReader(new BufferedReader(new StringReader(relationSetFile)));

        Map<String, List<String>> embeddings = Maps.newHashMap();

        List<String> result = relationSet.getEmbeddedRelations(relation1, embeddings);
        assertEquals(1, result.size());
        TestUtil.assertCount(result, relation1, 1);

        result = relationSet.getEmbeddedRelations(relation1, null);
        assertEquals(1, result.size());
        TestUtil.assertCount(result, relation1, 1);

        // Using Arrays.asList here is immutable, so this will catch a bug I had earlier where the
        // embeddings got modified by this method.
        embeddings.put(relation1, Arrays.asList(embedded1, embedded2));

        result = relationSet.getEmbeddedRelations(relation1, embeddings);
        assertEquals(2, result.size());
        TestUtil.assertCount(result, embedded1, 1);
        TestUtil.assertCount(result, embedded2, 1);

        relationSetFile = "keep original edges\ttrue\n";
        relationSet = RelationSet.fromReader(new BufferedReader(new StringReader(relationSetFile)));
        result = relationSet.getEmbeddedRelations(relation1, embeddings);
        assertEquals(3, result.size());
        TestUtil.assertCount(result, embedded1, 1);
        TestUtil.assertCount(result, embedded2, 1);
        TestUtil.assertCount(result, relation1, 1);
    }

    public void testWriteRelationEdges() throws IOException {
        // First we test a surface relation set.
        String relationSetFile = "is kb\tfalse\n";
        RelationSet relationSet =
                RelationSet.fromReader(new BufferedReader(new StringReader(relationSetFile)));

        String relationFile = "";
        relationFile += np1 + "\t" + verb1 + "\t" + np2 + "\n";
        relationFile += np1 + "\t" + verb2 + "\t" + np2 + "\n";

        Map<String, List<String>> embeddings = new HashMap<String, List<String>>();
        embeddings.put(verb1, Lists.newArrayList(embedded1, embedded2));

        Set<String> seenNps = new HashSet<String>();

        List<Pair<String, Map<String, List<String>>>> aliases = Lists.newArrayList();
        Map<String, List<String>> aliasSet = Maps.newHashMap();
        aliasSet.put(np1, Lists.newArrayList(concept1));
        aliasSet.put(np2, Lists.newArrayList(concept1, concept2));
        aliases.add(new Pair<String, Map<String, List<String>>>("alias", aliasSet));

        FakeFileWriter writer = new FakeFileWriter();

        BufferedReader reader = new BufferedReader(new StringReader(relationFile));
        List<String> expected = Lists.newArrayList();
        // Np-to-concept edges.
        expected.add(np1Index + "\t" + concept1Index + "\t" + aliasRelationIndex + "\n");
        expected.add(np2Index + "\t" + concept1Index + "\t" + aliasRelationIndex + "\n");
        expected.add(np2Index + "\t" + concept2Index + "\t" + aliasRelationIndex + "\n");
        // And verb edges.
        expected.add(np1Index + "\t" + np2Index + "\t" + embedded1Index + "\n");
        expected.add(np1Index + "\t" + np2Index + "\t" + embedded2Index + "\n");
        expected.add(np1Index + "\t" + np2Index + "\t" + verb2Index + "\n");
        int numEdges = relationSet.writeRelationEdgesFromReader(reader,
                                                                embeddings,
                                                                null,
                                                                prefix,
                                                                seenNps,
                                                                aliases,
                                                                writer,
                                                                nodeDict,
                                                                edgeDict);
        assertEquals(expected.size(), numEdges);
        writer.expectWritten(expected);

        // Now test it for just alias edges.
        relationSetFile += "aliases only\ttrue\n";
        relationSet = RelationSet.fromReader(new BufferedReader(new StringReader(relationSetFile)));
        reader = new BufferedReader(new StringReader(relationFile));
        expected = Lists.newArrayList();
        seenNps = new HashSet<String>();
        // Np-to-concept edges.
        expected.add(np1Index + "\t" + concept1Index + "\t" + aliasRelationIndex + "\n");
        expected.add(np2Index + "\t" + concept1Index + "\t" + aliasRelationIndex + "\n");
        expected.add(np2Index + "\t" + concept2Index + "\t" + aliasRelationIndex + "\n");
        numEdges = relationSet.writeRelationEdgesFromReader(reader,
                                                            embeddings,
                                                            null,
                                                            prefix,
                                                            seenNps,
                                                            aliases,
                                                            writer,
                                                            nodeDict,
                                                            edgeDict);
        assertEquals(expected.size(), numEdges);
        writer.expectWritten(expected);

        // Now some KB relations.
        relationSetFile = "is kb\ttrue\n";
        relationSet = RelationSet.fromReader(new BufferedReader(new StringReader(relationSetFile)));
        relationFile = "";
        relationFile += concept1 + "\t" + concept2 + "\t" + relation1 + "\n";
        relationFile += concept1 + "\t" + concept2 + "\t" + relation2 + "\n";
        reader = new BufferedReader(new StringReader(relationFile));
        embeddings = Maps.newHashMap();
        embeddings.put(relation1, Lists.newArrayList(embedded1, embedded2));
        expected = Lists.newArrayList();
        expected.add(concept1Index + "\t" + concept2Index + "\t" + embedded1Index + "\n");
        expected.add(concept1Index + "\t" + concept2Index + "\t" + embedded2Index + "\n");
        expected.add(concept1Index + "\t" + concept2Index + "\t" + relation2Index + "\n");
        numEdges = relationSet.writeRelationEdgesFromReader(reader,
                                                            embeddings,
                                                            null,
                                                            prefix,
                                                            seenNps,
                                                            aliases,
                                                            writer,
                                                            nodeDict,
                                                            edgeDict);
        assertEquals(expected.size(), numEdges);
        writer.expectWritten(expected);

        // And some quick tests to make sure that the prefix behavior is correct.
        relationSetFile += "relation prefix\t" + prefix + "\n";
        relationSet = RelationSet.fromReader(new BufferedReader(new StringReader(relationSetFile)));
        relationFile = concept1 + "\t" + concept2 + "\t" + relation1 + "\n";
        reader = new BufferedReader(new StringReader(relationFile));
        embeddings = Maps.newHashMap();
        expected = Lists.newArrayList();
        expected.add(concept1Index + "\t" + concept2Index + "\t" + relation1Index + "\n");
        numEdges = relationSet.writeRelationEdgesFromReader(reader,
                                                            embeddings,
                                                            null,
                                                            null,
                                                            seenNps,
                                                            aliases,
                                                            writer,
                                                            nodeDict,
                                                            edgeDict);
        assertEquals(expected.size(), numEdges);
        writer.expectWritten(expected);

        relationSetFile = "is kb\ttrue\n";
        relationSet = RelationSet.fromReader(new BufferedReader(new StringReader(relationSetFile)));
        reader = new BufferedReader(new StringReader(relationFile));
        expected = Lists.newArrayList();
        expected.add(concept1Index + "\t" + concept2Index + "\t" + relationNoPrefix1Index + "\n");
        numEdges = relationSet.writeRelationEdgesFromReader(reader,
                                                            embeddings,
                                                            null,
                                                            null,
                                                            seenNps,
                                                            aliases,
                                                            writer,
                                                            nodeDict,
                                                            edgeDict);
        assertEquals(expected.size(), numEdges);
        writer.expectWritten(expected);
    }
}
