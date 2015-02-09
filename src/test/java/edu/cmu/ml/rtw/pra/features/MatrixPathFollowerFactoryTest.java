package edu.cmu.ml.rtw.pra.features;

import java.io.IOException;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;

import edu.cmu.ml.rtw.pra.config.PraConfig;
import edu.cmu.ml.rtw.pra.experiments.Dataset;
import edu.cmu.ml.rtw.pra.experiments.FakeDatasetFactory;
import edu.cmu.ml.rtw.users.matt.util.Dictionary;
import edu.cmu.ml.rtw.users.matt.util.Pair;

public class MatrixPathFollowerFactoryTest extends TestCase {

  public void testCreateGivesCorrectMatrixDir() throws IOException {
    List<PathType> pathTypes = Lists.newArrayList();
    Dataset d = new FakeDatasetFactory().fromFile("fake", new Dictionary());
    List<Pair<Pair<Integer, Integer>, Integer>> e = Lists.newArrayList();

    PraConfig config = new PraConfig.Builder()
        .noChecks().setMatrixDir("matrix dir").setGraph("graph dir/graph_chi/edges.tsv").build();
    PathFollower follower = new MatrixPathFollowerFactory().create(pathTypes, config, d, e);
    assertEquals("graph dir/matrix dir/", ((MatrixPathFollower)follower).getMatrixDir());

    config = new PraConfig.Builder()
        .noChecks().setGraph("graph dir/graph_chi/edges.tsv").build();
    follower = new MatrixPathFollowerFactory().create(pathTypes, config, d, e);
    assertEquals("graph dir/matrices/", ((MatrixPathFollower)follower).getMatrixDir());
  }
}
