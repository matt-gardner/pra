---
layout: page
title: PRA Parameters
---
# PRA Parameters

These parameters specify things like how many random walks to do, L2 and L1 weight, and whether to
use random walks or matrix multiplication to compute feature values.

Available parameters:
 * `mode`: how should we run PRA?  This currently has three options: `no op`, `standard` (the
   default), and `exploration`.  `no op` lets you use an experiment specification to just create a
graph or a dataset - it does all the preporatory stuff for PRA, then just quits.  `standard` runs
the normal PRA code, with four main steps: (1) do random walks over the training data to find path
types to use as features in the PRA model; (2) do random walks that are constrained to follow each
path type, to compute feature values for the PRA model; (3) run logistic regression to learn
weights for each of the path features found; (4) repeat step 2 with the test data, then classify it
with the learned model.  `exploration` stops after the first step of PRA, and outputs a more
verbose version of the results.  Instead of just outputting statistics about each path seen,
`exploration` will output all paths seen for each (source, target) pair in the training data.  This
is useful if you want to see what's going on in your data, or if you have a method that can make
use of more path types than PRA can compute feature values for, and you don't care about the
specific probabilities that PRA calculates in the second step.  The results from using the
`standard` mode are put in the `results/` directory, while `exploration` results are put in
`results_exploration/` (`ExperimentScorer` looks in `results/` for methods to compare, and it would
get confused if it tried to run on something output in `exploration` mode).

* `L1 weight`: used when training the logistic regression classifier.

* `L2 weight`: used when training the logistic regression classifier.

* `walks per source`: during the path finding step, how many walks should we do from each training
  node?

* `path finding iterations`: at each iteration, each walk from each source and target node takes at
  least one step.  The code keeps track of where the walks go to, and joins paths through
intermediate nodes.  So if this is set to 2, for instance, you can find paths of up to length 4
(and possibly longer, because of the "at least one" bit above - the details aren't important here).

* `number of paths to keep`: how many of the paths that we found in the path finding step should we
  keep?  The higher this is, the longer the path following step will take.  The paths will be
selected by the PathTypeSelector, which has a parameter below (but so far not many options - the
default is just to select the most frequent).  You can also set this to -1 to keep all paths that
are found, but be aware that this might be a really bad idea.  There could potentially be tens or
hundreds of thousands of paths found, depending on your graph and your parameters for path finding,
and keeping all of them would be prohibitively expensive in the path following step.  It's there if
you want it, but you should consider if just using the PathExplorer (with the ExperimentExplorer
driver) is sufficient for what you're trying to do.

* `walks per path`: during the path following step, how many walks should we do per (source node,
  path type) pair?

* `normalize walk probabilities`: after all of the random walks are finished, PRA typically
  computes feature values by normalizing the probability distribution over target nodes, given a
(source node, path type) pair.  If you specify this as false, that step will be skipped.  I don't
think this makes a big difference in either performance or running time, but if you want to
experiment with it you can.

* `binarize features`: when passing features into logistic regression, if this is true we will
  ignore the feature value computed and just use binary feature values.  This doesn't seem to make
much difference in performance.

* `matrix accept policy`: this determines which (source, target) pairs should be kept when
  computing a feature matrix.  PRA does random walks from all source nodes in your dataset, and
keeps track of _all_ targets that are reached.  We generally use non-input targets as negative
examples at training time, as PRA typically only has positive training data.  But we give a few
options here, in case you actually have negative examples.  The first (and default, and recommended
for most use cases) is `all-targets`.  This requires that a range be specified in the
[KB files]({{ site.baseurl }}/input/kb_files.html) for the relation currently being learned, and
restricts entries in the matrix to have only targets from the given range (so, for example, if we
end up at a city when we're trying to predict a country, we don't use that as a negative example
when training, nor do we try to score it as a prediction at test time).  A more permissive option
is `everything`, which allows _all_ targets into the feature matrix.  This is not recommended, but
is necessary if you don't have a range for the relation you're trying to learn (unless you have
your own negative examples, at training _and_ test time).  The final option is
`paired-targets-only`.  This means that the code will _only_ produce feature matrix rows for
examples that are explicitly listed in the input dataset (both at training time and at testing
time; if you want to do this only at training time, but use `all-targets` or `everything` at test
time, you'll have to modify and recompile the code.  Sorry.  You can send me a pull request when
you're done, though =) ).  If you have your own negative examples, and you don't want to augment
them with whatever other examples the random walks finds, then this is what you should use (to be
clear: use `all-targets` if you want to use your own negative examples _and_ whatever the random
walks find; use `paired-targets-only` to _only_ use your provided negative examples).  Note that
this parameter only affects the _feature computation_ step; it does not affect learning in any way,
other than determining how the feature matrix used at learning time is computed.

* `path accept policy`: this was intended to be similar to `matrix accept policy` for the _feature
  selection_ step, but there's really only one good option.  Use `paired-only`, which means you
only accept paths that go from a given source to its paired target; the `everything` option, which
accepts paths from _any_ source to _any_ target, turned out to be way too computationally
expensive.

* `path follower`: should we use random walks or matrix multiplication to compute feature values?
  Current the only options here are "random walks" or "matrix multiplication", and the default is
"random walks".  I'm still evaluating the performance characteristics of these two, but it seems
like matrix multiplication might be a bit faster (if you have the fortran matrix libraries
installed), but also have slightly lower performance, for reasons I won't go into here.

* `max matrix feature fan out`: this is to bound the size of the feature matrix computed using
  matrix mutliplication.  If there are more than this number of targets for every source node
given a path, the feature will be dropped.  Default is 100.

* `path type factory`: this tells the code what kind of path types to use.  At the moment there are
  just two kinds, though I may add more in the near future.  The default path type is just a
sequence of edge types.  The only current alternative is a sequence of edge types with associated
vectors, which implements my vector space random walks.  So this parameter is how you specify that
you want to use this new method.  In contrast to all of the above parameters, which take simple
types as their values, this requires an object.  Here's an example:

```
{
  "path type factory": {
    "name": "VectorPathTypeFactory",
    "spikiness": 3,
    "reset weight": 0.25,
    "embeddings": {
      "name": "synthetic",
      "graph": "synthetic",
      "dims": 50
    }
    "matrix dir": "denser_matrices"
  }
}
```

See the paper for what `spikiness` and `reset weight` do (the code wasn't very sensitive to small
changes to the values you see above).  For the vector space embeddings, you can either create your
own and just give a name or a path, or you can specify a graph and a number of dimensions, and the
code will perform a sparse SVD on the graph.  You should probably be sure that you have fortran
bindings for the matrix libraries if you want to have the PRA code do the SVD and your graph is
large.  I use [breeze](http://github.com/scalanlp/breeze) for this, so see their instructions for
setting up the fortran bindings, and ask either them or me if you have trouble with it.  If you
create your own embeddings, the format the code expects is just one relation per line, with the
relation name followed by the value of each dimension, all tab-separated.  Have the code create
embeddings on a small graph if you need an example.  Also note that you can give a list here for
the `embeddings` parameter, so you can use several kinds of embeddings if you want, though that's
not necessarily advisable.

The `matrix dir` parameter is used when you want to combine the matrix multiplication PRA
implementation with vector space random walks.  The gist is that you use the embeddings to create a
denser matrix that is used instead of the adjacency matrix when computing feature values (the
denser matrix is specified in the [graph parameters]({{ site.baseurl }}/input/graphs.html).  I'm
still working on the right way to construct this denser matrix to get performance similar to the
vector space random walks, but the functionality is there if you want to play around with it.

* `path type selector`: I tried to make a selector that clusters path types based on vector space
  similarity, but it didn't end up helping at all (I think there's some potential there, but the
main issue I find with KB inference is sparsity in the graph, not sparsity in the path types, so
this was unnecessary).  So as of now, unless you want to experiment with that, just leave this
blank, as the only other selector is one that selects by frequency.  I'm planning on adding a
couple more of these in the near future that try to be smarter about selecting path types, so stay
tuned.

You can see some default values I use for these in the `examples/` directory of the [PRA
codebase](http://github.com/matt-gardner/pra).
