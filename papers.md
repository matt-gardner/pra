---
title: Papers
layout: page
---
# Papers Using This Code

* Incorporating Vector Space Similarity in Random Walk Inference over Knowledge Bases.  Matt
  Gardner, Partha Talukdar, Jayant Krishnamurthy, and Tom Mitchell.  EMNLP 2014.
  ([website](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra))
  ([github release](https://github.com/matt-gardner/pra/releases/tag/emnlp2014))
* Improving Learning and Inference in a Large Knowledge-base using Latent Syntactic Cues.  Matt
  Gardner, Partha Talukdar, Bryan Kisiel, and Tom Mitchell.  EMNLP 2013.
  ([website](http://rtw.ml.cmu.edu/emnlp2013_pra))
  ([github release](https://github.com/matt-gardner/pra/releases/tag/emnlp2013))

Each of these papers has a corresponding tag in the repository (called a "release" in github).  You
can check out that tag if you _really_ want to reproduce _exactly_ the experiments that I ran for
those papers (and I actually broke the emnlp2013 release by removing the jars that were stored in
this repo, to drammatically cut down download size...).  But you probably should just use the most
recent code - this codebase will run all of the methods used in those papers, if not with exactly
the same code.

If you use this code in research that you publish, please cite the relevant paper that you're
comparing to.  If you're not comparing to any of the papers above, but doing something different
with the code, please let me know, I'd be interested in hearing about it.

## Note on using new code with the EMNLP 2014 data download

Since running the experiments for the EMNLP 2014 paper, I have updated the GraphChi code that does
the graph processing for PRA.  This means that the sharding done now by GraphChi is incompatible
with the sharding in the EMNLP 2014 data download, and so it's likely that the experiments won't
work correctly when trying to use the graphs.  There's really only one good way to fix this:
re-run the sharding yourself, manually (I should fix the download so you don't have to do this,
but I haven't gotten to it yet...).  It's not that hard; here are some steps to fix it:

1. From a shell, remove all files in the graph directory under `graph_chi/` _except_
   `graph_chi/edges.tsv`.

2. `GraphCreator.shardGraph` is currently a private method.  Make it public static, just to make
   this easy.

3. run `sbt console`, to get an interpretor that will let you run the code

4. Run the `shardGraph` method on the graph that you want.  That should look something like this:
```
edu.cmu.ml.rtw.pra.graphs.GraphCreator.shardGraph(
  "/path/to/graph/dir/graph_chi/edges.tsv",
  n)
```
where `n` is the number in the `num_shards.tsv` file in the graph directory.

If you wanted, you could write a script that would re-shard all of the graphs that you think you
might use, just so you don't have to worry about this in the future.  Let me know if you have any
trouble with this.
