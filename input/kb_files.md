---
layout: page
title: Experiment Spec
---
# KB Files Format

This is a directory that contains information about a KB.  It generally has the following entries:

* `category_instances/`: A directory containing all of the instances of each category, one category
  per file (format is just one instance per line).  This is necessary for restricting the
predictions that PRA makes - the `category_instances/` file has all acceptable target nodes for
each relation.

* `domains.tsv`: A mapping from relations to the category corresponding to the relation's domain.
  Currently unused.  Format is `[relation] \t [domain]`, where `domain` must have a corresponding
file in `category_instances/`.

* `ranges.tsv`: A mapping from relations to the category corresponding to the relation's range.
  Used to restrict PRA's predictions.  Format is `[relation] \t [range]`, where `range` must have a
corresponding file in `category_instances/`.

* `inverses.tsv`: A mapping from relations to their inverses.  This is used for restricting the
  random walks.  When training a PRA classifier to predict a relation, you need to "remove" the
edges that correspond to the training data, so you don't learn things that won't be useful, and
edges that correspond to the testing data, so you're not cheating.  If there are known inverses in
the graph, we need to remove both the original edge and its inverse, when applicable.

* `labeled_edges.tsv`: Contains all relation instances in the KB, each of which will correspond to
  a single edge in a PRA graph.  This file is generally referenced in a relation set, when creating
graphs, as described below.

* `relations/`: A directory containing all of the instances of each relation, one relation per
  file.  This is used to simplify the process of cross validation, when a fixed training/testing
set isn't specified in a split directory (see below).

If you are using Freebase, you shouldn't have to create these directories manually.  There is a
class in the PRA code base, `graphs.FreebaseKbFilesCreator`, that does this for you.  You could
also download the data files from my EMNLP 2014 paper, as it contains KB files for NELL and
Freebase.  (I didn't include the NellKbFilesCreator in this repository, because it has too many
dependencies on the NELL codebase - I would have had to copy too many files over.  If you really
want to create new KB files for a newer version of NELL than you can get from the data files in the
EMNLP 2014 paper, talk to me.)

If you are using PRA with a set of relations that has none of this metadata, such as learning
models for open-domain relations, I'm pretty sure this directory can be left empty (though you'll
want to change a few of the settings in your [parameter specification file]({{ site.baseurl
}}/input/param_file.html)).  If you run into issues with this, let me know.
