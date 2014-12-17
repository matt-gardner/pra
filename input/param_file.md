---
layout: page
title: Paramter File
---
# Parameter File Format

A parameter file specifies things like how many random walks to do, L2 and L1 weight, and other
things.  The file should be tab separated, one parameter per line.  See `param_files/` for
examples.

Available parameters:

* `L1 weight`

* `L2 weight`

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

* `matrix accept policy`: this determines which (source, target) pairs should be kept when
  computing a feature matrix.  PRA does random walks from all source nodes in your dataset, and
keeps track of _all_ targets that are reached.  We generally use non-input targets as negative
examples at training time, as PRA typically only has positive training data.  But we give a few
options here, in case you actually have negative examples.  The first (and default, and recommended
for most use cases) is `all-targets`.  This requires that a range be specified in the [KB files]({{
site.baseurl }}/input/kb_files.html) for the relation currently being learned, and restricts
entries in the matrix to have only targets from the given range (so, for example, if we end up at a
city when we're trying to predict a country, we don't use that as a negative example when training,
nor do we try to score it as a prediction at test time).  A more permissive option is `everything`,
which allows _all_ targets into the feature matrix.  This is not recommended, but is necessary if
you don't have a range for the relation you're trying to learn (unless you have your own negative
examples, at training _and_ test time).  The final option is `paired-targets-only`.  This means
that the code will _only_ produce feature matrix rows for examples that are explicitly listed in
the input dataset (both at training time and at testing time; if you want to do this only at
training time, but use `all-targets` or `everything` at test time, you'll have to modify and
recompile the code.  Sorry.  You can send me a pull request when you're done, though =) ).  If you
have your own negative examples, and you don't want to augment them with whatever other examples
the random walks finds, then this is what you should use (to be clear: use `all-targets` if you
want to use your own negative examples _and_ whatever the random walks find; use
`paired-targets-only` to _only_ use your provided negative examples).  Note that this parameter
only affects the _feature computation_ step; it does not affect learning in any way, other than
determining how the feature matrix used at learning time is computed.

* `path accept policy`: this was intended to be similar to `matrix accept policy` for the _feature
  selection_ step, but there's really only one good option.  Use `paired-only`, which means you
only accept paths that go from a given source to its paired target; the `everything` option, which
accepts paths from _any_ source to _any_ target, turned out to be way too computationally
expensive.

* `path type embeddings`: this is how you specify that you want to use vector space random walks
  (my most recent, and best performing, method).  Specifying the parameter is a little cludgy, but
it should look like this: "[spikiness],[reset prob],[path to embeddings]".  Good values for
spikiness are around 3, and good values for reset prob are anywhere from .75 - .25 (it didn't seem
to make a lot of difference).  Again, see the example files in `param_files/vector*params.tsv`

* `path type selector`: I tried to make a selector that clusters path types based on vector space
  similarity, but it didn't end up helping at all (I think there's some potential there, but the
main issue I find with KB inference is sparsity in the graph, not sparsity in the path types, so
this was unnecessary).  So as of now, unless you want to experiment with that, just leave this
blank, as the only other selector is one that selects by frequency.  I should add some that select
by mutual information or other metrics, but they would add another random walk step, which might be
prohibitively expensive...

You can see the default values I use for these in the example param files that I've already
created.
