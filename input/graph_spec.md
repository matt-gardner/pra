---
layout: page
title: Graph Spec
---
# Graph Spec File Format

The graph specification is a tab-separated file, where one parameter is
specified per line.  There are currently only two parameters:

* `relation set`: a path to a [relation set]({{ site.baseurl }}/input/relation_sets.html)
  specification (can be given multiple times).

* `deduplicate edges`: either `true` or `false` (or just not present, which
  defaults to `false`).  If `true`, graph creation takes slightly more time
  and memory, but all duplicate edges are removed from the graph.

An example graph specification file is as follows (note that tabs may not
appear correctly below, but must be used in the spec file):

```
relation set    /Users/mgardner/ai2/pra/relation_sets/ai2_openie.tsv
relation set    /Users/mgardner/ai2/pra/relation_sets/ppdb.tsv
relation set    /Users/mgardner/ai2/pra/relation_sets/additional_svo.tsv
deduplicate edges       true
```

Upon running `ExperimentGraphCreator`, a [graph]({{ site.baseurl }}/input/graphs.html)
will be created under `graphs/` for each spec file found in `graph_specs/`.
