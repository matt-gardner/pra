---
title: Papers
layout: page
---
# Papers Using This Code

* Incorporating Vector Space Similarity in Random Walk Inference over Knowledge
  Bases.  Matt Gardner, Partha Talukdar, Jayant Krishnamurthy, and Tom
  Mitchell.  EMNLP 2014.
  ([website](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra))
  ([github release](https://github.com/matt-gardner/pra/releases/tag/emnlp2014))
* Improving Learning and Inference in a Large Knowledge-base using Latent
  Syntactic Cues.  Matt Gardner, Partha Talukdar, Bryan Kisiel, and Tom
  Mitchell.  EMNLP 2013.  ([website](http://rtw.ml.cmu.edu/emnlp2013_pra))
  ([github release](https://github.com/matt-gardner/pra/releases/tag/emnlp2013))

Each of these papers has a corresponding tag in the repository (called a
"release" in github).  You can check out that tag if you _really_ want to
reproduce _exactly_ the experiments that I ran for those papers (and I actually
broke the emnlp2013 release by removing the jars that were stored in this repo,
to drammatically cut down download size...).  But you probably should just use
the most recent code - this codebase will run all of the methods used in those
papers, if not with exactly the same code.

If you use this code in research that you publish, please cite the relevant
paper that you're comparing to.  If you're not comparing to any of the papers
above, but doing something different with the code, please let me know, I'd be
interested in hearing about it.
