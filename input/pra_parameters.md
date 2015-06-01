---
layout: page
title: PRA Parameters
---
# PRA Parameters

These parameters specify things like how many random walks to do, L2 and L1 weight, and whether to
use random walks or matrix multiplication to compute feature values.  The parameters generally
follow the object structure of the code, which follows the execution model of PRA.  So, there is a
fair amount of nesting in these parameters, with each level getting passed the parameters it needs
to initialize some particular object in the code.  For example, all of the parameters necessary for
constructing feature matrices are nested under `features`, while those dealing with learning
classification models using those feature matrices are under `learning`.  You can often see what
parameters are available in the code by looking near the top of an object for the code
`JsonHelper.ensureNoExtras(...)`.  I'll try to document most of what's available here, but doing a
search for that line in the code will give you an up-to-date view of what parameters are actually
available.  Also, feel free to ask me (or open an issue on the github project) if some parameter
doesn't work or you have questions.

Also note that there is often a parameter at each level that will change which other parameters
are allowed.  For instance, different options are allowed at the top level depending on which
`mode` setting is used.

Finally, if this documentation format is confusing, just look at the [examples in the github
repository](https://github.com/matt-gardner/pra/tree/master/examples).  This page tries to document
most of what is going on in the examples in that directory.

## Top-level PRA parameters

These parameters are nested under `pra parameters` in the spec file.  `mode` is the main
parameter, and determines what else is allowed.

 * `mode`: how should we run PRA?  This currently has three options: `no op`, `learn models` (the
   default), and `explore graph`.  `no op` lets you use an experiment specification to just create
a graph or a dataset - it does all the preporatory stuff for PRA, then just quits.  `learn models`
runs the normal PRA code, with four main steps: (1) do random walks over the training data to find
path types to use as features in the PRA model; (2) do random walks that are constrained to follow
each path type, to compute feature values for the PRA model; (3) run logistic regression to learn
weights for each of the path features found; (4) repeat step 2 with the test data, then classify it
with the learned model.  `explore graph` stops after the first step of PRA, and outputs a more
verbose version of the results.  Instead of just outputting statistics about each path seen,
`explore graphs` will output all paths seen for each (source, target) pair in the training data.
This is useful if you want to see what's going on in your data, or if you have a method that can
make use of more path types than PRA can compute feature values for, and you don't care about the
specific probabilities that PRA calculates in the second step.  The results from using the `learn
models` mode are put in the `results/` directory, while `explore graphs` results are put in
`results_exploration/` (`ExperimentScorer` looks in `results/` for methods to compare, and it would
get confused if it tried to run on something output in `explore graphs` mode).

Top-level PRA parameters for mode `no op`:
* No other parameters are looked at with this mode.

Top-level PRA parameters for mode `learn models`:
* `features`: Under here you specify all of the parameters for generating a feature matrix.  The
  code for this is in the `FeatureGenerator` trait (and subclasses `PraFeatureGenerator` and
`SubgraphFeatureGenerator`).

* `learning`: Under here you specify all of the parameters for learning models.  `PraModel` is the
  class that takes these parameters, with subclasses `LogisticRegressionModel` and `SVMModel`.

Top-level PRA parameters for mode `explore graphs`:
* `data`: This parameter says whether to use the training data, the test data, or both (from the
  split [specified elsewhere]({{ site.baseurl }}/input/split_dir.html)).

* `explore`: The parameters in here are passed to the `GraphExplorer` object, and are basically
  the same as those passed to a `PathFinder`, which you can see below.  Look at the
`GraphExplorer` code if you have questions; the parameters are listed at the top of the file.

## FeatureGenerator parameters

These parameters show up under `features`, explained above.  There are a lot of potential
parameters here, and they depend on the feature generator _type_, which is the main parameter here.

* `type`: Available values are `pra` and `subgraphs`.  `pra` does the standard two-step process
  for generating a PRA feature matrix.  `subgraphs` uses a faster technique that basically only
does the first step of PRA.

Parameters for type `pra` (see the top of `PraFeatureGenerator`):
* `path finder`: These parameters get passed to a `PathFinder` object, which is the first step of
  PRA.

* `path selector`: These parameters get passed to a `PathTypeSelector` object, which is the end of
  the first step of PRA.

* `path follower`: These parameters get passed to a `PathFollower` object, which is the second
  step of PRA.

Parameters for type `subgraphs` (see the top of `SubgraphFeatureGenerator`):

* `path finder`: Same as the `path finder` parameter for `PraFeatureGenerator`.

* `feature extractors`: This is a list of `FeatureExtractor` specifications, which operate on the
  subgraphs found by the `PathFinder`.  See the example directory in the code for how to use these.

* `feature size`: This allows for feature hashing, if the feature vectors get too large to be
  manageable.  This was experimental, and I didn't find it to be that useful.

* `include bias`: This determines whether we add a bias term to the generated feature matrices.  I
  found that including a bias term actually hurt mean average precision, so this is false by
default.

## PathFinder parameters

Again here there is a `type` parameter which determines which `PathFinder` is used.

* `type`: There are two options: `RandomWalkPathFinder` and `BfsPathFinder`.
  `RandomWalkPathFinder` uses GraphChi to actually perform random walks to find paths, while
`BfsPathFinder` loads the graph into memory and uses a breadth-first search when finding paths.

Parameters for `RandomWalkPathFinder`:

* `walks per source`: during the path finding step, how many walks should we do from each training
  node?

* `path finding iterations`: at each iteration, each walk from each source and target node takes at
  least one step.  The code keeps track of where the walks go to, and joins paths through
intermediate nodes.  So if this is set to 2, for instance, you can find paths of up to length 4
(and possibly longer, because of the "at least one" bit above - the details aren't important here).

* `reset probability`: Each walk has some probability of restarting at every step.  This allows you
  to change that probability.  The default used to be .15, but now it is 0.  If you have a lot of
path finding iterations, you might want to allow the walks to restart occasionally.

* `path accept policy`: this was intended to be similar to `matrix accept policy` for the _feature
  selection_ step, but there's really only one good option.  Use `paired-only`, which means you
only accept paths that go from a given source to its paired target; the `everything` option, which
accepts paths from _any_ source to _any_ target, turned out to be way too computationally
expensive.

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

Parameters for `BfsPathFinder`:
* `number of steps`: How many steps should the BFS take?  Default is currently 2.

* `max fan out`: If at any particular node, a edge type has more than this many outgoing edges, we
  stop the BFS on that edge type at that node.  This is to control the exponential time complexity
of the BFS, especially when dealing with category or type nodes in a knowledge base graph.  The
default is currently 100.

## PathTypeSelector parameters

These parameters go under `features -> path selector` when the feature type is `pra`.  There is
currently just one parameter here.

* `number of paths to keep`: how many of the paths that we found in the path finding step should we
  keep?  The higher this is, the longer the path following step will take.  The paths will be
selected by the PathTypeSelector, which has a parameter below (but so far not many options - the
default is just to select the most frequent).  You can also set this to -1 to keep all paths that
are found, but be aware that this might be a really bad idea.  There could potentially be tens or
hundreds of thousands of paths found, depending on your graph and your parameters for path finding,
and keeping all of them would be prohibitively expensive in the path following step.  It's there if
you want it, but you should consider if just using the PathExplorer (with the ExperimentExplorer
driver) is sufficient for what you're trying to do.

Well, you can also specify a `name`, and use an experimental `PathTypeSelector` that I tried using
with the vector space random walks in my EMNLP 2014 paper.  I'm not going to document it here,
though; look for the `createPathTypeSelector` method if you really care.

## PathFollower parameters

These parameters go under `features -> path follower` when the feature type is `pra`.  There are a
few different `PathFollowers` implemented, but I'm just going to document the main one,
`RandomWalkPathFollower`.  See the `createPathFollower` code in the `Driver` class for more
information on the others (or the `PathFollower` class itself, if I ever get it moved to scala).

Avaliable parameters:

* `walks per path`: during the path following step, how many walks should we do per (source node,
  path type) pair?

* `normalize walk probabilities`: after all of the random walks are finished, PRA typically
  computes feature values by normalizing the probability distribution over target nodes, given a
(source node, path type) pair.  If you specify this as false, that step will be skipped.  I don't
think this makes a big difference in either performance or running time, but if you want to
experiment with it you can.

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

## Learning parameters

These parameters go under `pra parameters -> learning`, and there are two types available.

* `type`: What kind of model should we use?  The available options are `logistic regression` and
  `svm`.

* `binarize features`: both kinds of models accept this parameter, which determines whether the
  features computed by the `FeatureGenerator` are binarized before being passed to the learning
model.

Logistic regression params:

* `L1 weight`: used when training the logistic regression classifier.

* `L2 weight`: used when training the logistic regression classifier.

SVM params:

* `kernel`: What kernel should be used?  Available options are `linear`, `quadratic`, and `rbf`;
  none of these performed as well as logistic regression in my experiments with it, however.
Perhaps training the SVM with some kind of ranking loss would give better performance on mean
average precision.
