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

* `walks per path`: during the path following step, how many walks should we do
  per (source node, path type) pair?

* `path finding iterations`

* `number of paths to keep`

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

* `path type selector`: this was experimental, and it didn't turn out to help
  at all, so you don't need to worry about it.  I'm just putting it here for
  completeness.

You can see the default values I use for these in the example param files that
I've already created.
