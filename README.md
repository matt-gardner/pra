PRA (Path Ranking Algorithm)
============================

An implementation of the Path Ranking Algorithm (PRA) using GraphChi, a library for efficient
processing of large graphs on a single machine.  This algorithm learns models that analyze a graph
and predict missing edges of particular types.  The code here was used to run experiments in the
following papers:

* Incorporating Vector Space Similarity in Random Walk Inference over Knowledge Bases.  Matt
  Gardner, Partha Talukdar, Jayant Krishnamurthy, and Tom Mitchell.  EMNLP 2014.
([website](http://rtw.ml.cmu.edu/emnlp2014_vector_space_pra))
* Improving Learning and Inference in a Large Knowledge-base using Latent Syntactic Cues.  Matt
  Gardner, Partha Talukdar, Bryan Kisiel, and Tom Mitchell.  EMNLP 2013.
([website](http://rtw.ml.cmu.edu/emnlp2013_pra))

See [the github.io page](http://matt-gardner.github.io/pra/) for code documentation.  Please feel
free to file bugs, feature requests, or send pull requests.

License
=======

This code makes use of a number of other libraries that are distributed under various open source
licenses (notably the Apache License and the Common Public License).  You can see those
dependencies listed in the pom.xml file.  The code under the src/ directory is distributed under
the terms of the GNU General Public License, version 3 (or, at your choosing, any later version of
that license).  You can find the text of that license
[here](http://www.gnu.org/licenses/gpl-3.0.txt).

WARNING
=======

I did a force push on the repository on September 15, 2014, to remove some old jar files that made
the download for this repository about 25MB.  Now it's about ~200KB.  I thought that breaking the
history was worth it, given the small number of current users of the code, and the fact that I'm
the only one contributing right now.  What this means is that if you cloned the repository before
Sept. 15, 2014, and you want to update the repository, you're going to have to re-clone the
repository.  The good news is that it shouldn't be that big a deal, because you probably didn't
modify the code, and the download is now just 200KB.
