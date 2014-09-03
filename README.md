PRA (Path Ranking Algorithm)
============================

An implementation of the Path Ranking Algorithm (PRA) using GraphChi, a library
for efficient processing of large graphs on a single machine.  This algorithm
learns models that analyze a graph and predict missing edges of particular
types.  The code here was used to run experiments in the following papers:

* Incorporating Vector Space Similarity in Random Walk Inference over
  Knowledge Bases.  Matt Gardner, Partha Talukdar, Jayant Krishnamurthy, and
  Tom Mitchell.  EMNLP 2014.  ([website](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra))
* Improving Learning and Inference in a Large Knowledge-base using Latent
  Syntactic Cues.  Matt Gardner, Partha Talukdar, Bryan Kisiel, and Tom
  Mitchell.  EMNLP 2013.  ([website](http://rtw.ml.cmu.edu/emnlp2013_pra))

Each of these papers has a corresponding tag in the repository (called a
"release" in github).  You can check out that tag if you _really_ want to
reproduce _exactly_ the experiments that I ran for those papers.  But you
probably should just use the most recent code - this codebase will run all of
the methods used in those papers, if not with exactly the same code.

See [the wiki](http://github.com/matt-gardner/pra/wiki/Documentation) for code
documentation.  Please feel free to file bugs, feature requests, or send pull
requests.

License
=======

This code makes use of a number of other libraries that are distributed under
various open source licenses (notably the Apache License and the Common Public
License).  You can see those dependencies listed in the pom.xml file.  The code
under the src/ directory is distributed under the terms of the GNU General
Public License, version 3 (or, at your choosing, any later version of that
license).  You can find the text of that license at the following location:
http://www.gnu.org/licenses/gpl-3.0.txt.
