---
layout: page
title: Paramter File
---
# Parameter File Format

A parameter file specifies things like how many random walks to do, L2 and L1
weight, and other things.  The file should be tab separated, one parameter per
line.  See `param_files/` for examples.

Available parameters:

* `L1 weight`

* `L2 weight`

* `walks per source`: during the path finding step, how many walks should we do
  from each training node?

* `path finding iterations`: at each iteration, each walk from each source and
  target node takes at least one step.  The code keeps track of where the walks
  go to, and joins paths through intermediate nodes.  So if this is set to 2,
  for instance, you can find paths of up to length 4 (and possibly longer,
  because of the "at least one" bit above - the details aren't important here).

* `number of paths to keep`: how many of the paths that we found in the path
  finding step should we keep?  The higher this is, the longer the path
  following step will take.  The paths will be selected by the
  PathTypeSelector, which has a parameter below (but so far not many options -
  the default is just to select the most frequent).

* `walks per path`: during the path following step, how many walks should we do
  per (source node, path type) pair?

* `only explicit negative evidence`: normally, we make use of (source, target)
  pairs that are found during the path following step as negative evidence, if
  they aren't given explicitly as positive examples (this corresponds to a
  closed world assumption over our training data).  Alternatively, you could
  specify negative examples explicitly in training, and then not use the
  unlabeled instances as negative.  If you want to know how to make this work
  right, ask me for help.

* `matrix accept policy`: related to the above parameter.  It determines which
  (source, target) pairs should be kept when computing a feature matrix.  Don't
  worry about it too much, just use "all-targets", as in the example files.

* `path accept policy`: also don't worry about this one too much, just use
  "paired-only".

* `path type embeddings`: this is how you specify that you want to use vector
  space random walks (my most recent, and best performing, method).  Specifying
  the parameter is a little cludgy, but it should look like this:
  "[spikiness],[reset prob],[path to embeddings]".  Good values for spikiness
  are around 3, and good values for reset prob are anywhere from .75 - .25 (it
  didn't seem to make a lot of difference).  Again, see the example files in
  `param_files/vector*params.tsv`

* `path type selector`: I tried to make a selector that clusters path types
  based on vector space similarity, but it didn't end up helping at all (I
  think there's some potential there, but the main issue I find with KB
  inference is sparsity in the graph, not sparsity in the path types, so this
  was unnecessary).  So as of now, unless you want to experiment with that,
  just leave this blank, as the only other selector is one that selects by
  frequency.  I should add some that select by mutual information or other
  metrics, but they would add another random walk step, which might be
  prohibitively expensive...

You can see the default values I use for these in the example param files that
I've already created.
