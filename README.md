# PRA (Path Ranking Algorithm)

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

# License

This code makes use of a number of other libraries that are distributed under various open source
licenses (notably the Apache License and the Common Public License).  You can see those
dependencies listed in the pom.xml file.  The code under the src/ directory is distributed under
the terms of the GNU General Public License, version 3 (or, at your choosing, any later version of
that license).  You can find the text of that license
[here](http://www.gnu.org/licenses/gpl-3.0.txt).

# Desired improvements

In rough order of priority.  I will probably do the top two things in the relatively near future.
The rest are kind of, "this would be nice, but I probably won't get to it any time soon".

- Better experiment analysis.  ExperimentScorer will currently only output a table with metrics
  over a whole dataset.  It would be nice if it (or another piece of code) would also do
significance testing, and output a table with individual relation results.

- Better parameter specification.  The current method for inputting parameters is something of a
  mess.  I started out using these TSV parameter files, then I added the .spec files, and they
really are redundant.  And if you want to use all of the same parameters except one, you need to
create a whole new file for them - being able to extend a parameter file would be nice.  This
really could be done better, and I'm sure there exist somewhat standard solutions for inputting
parameters; I should use one of them.

- Better feature selection.  The first step of PRA is selecting a set of path types that will be
  used as features in the rest of the algorithm.  That is currently done by using random walks to
find frequently seen path types.  It would be pretty simple to select features by some measure of
specificity, instead of simply by count, so that you have some hope of getting more useful features
out.

- Better negative example selection.  The main code path here is to specify only positive examples
  as the training data, and let the algorithm find negative examples using a closed-world
assumption.  It would be nice to have a better way of finding negative examples, then input them
as explicitly as negative examples, not bothering with any kind of closed world assumption.  Note
that if you have negative examples, the functionality for specifying negative examples and only
keeping specified rows in the feature matrix is already there.  There just isn't any kind of smart
technique for picking those negative examples.

- Single-sided features.  This is something that Ni Lao had in his implementation of PRA that I
  haven't done.  These act like biases on certain target or source nodes; for instance, this could
encode the fact that `Gender(X, Female)` has a very high negative weight for the relation
`FatherOf(X, Y)`.

- Allow for weighted edges.  The random walks I currently do cannot handle any kind of weights on
  the edges of the graph.  They might be useful in some circumstances.

- Better training methods.  Maximum likelihood estimation of a log-linear model might not be the
  best model we can use; it might be nice to have the option to use other loss functions or
training methods, like a ranking loss, or something.

# WARNING

I did a force push on the repository on September 15, 2014, to remove some old jar files that made
the download for this repository about 25MB.  Now it's about ~200KB.  I thought that breaking the
history was worth it, given the small number of current users of the code, and the fact that I'm
the only one contributing right now.  What this means is that if you cloned the repository before
Sept. 15, 2014, and you want to update the repository, you're going to have to re-clone the
repository.  The good news is that it shouldn't be that big a deal, because you probably didn't
modify the code, and the download is now just 200KB.
