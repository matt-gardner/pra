PRA (Path Ranking Algorithm)
============================

An efficient implementation of Ni Lao's Path Ranking Algorithm (PRA), using
GraphChi, a library for efficient processing of large graphs on a single
machine.  This also has the beginnings of my own improvements to the algorithm,
but there is more to come of that later.

The code is pretty well documented, I believe, and so I'm not going to put much
here.  Just compile it with ant (`ant compile`), and run it with `java -cp
lib/*:classes/ edu.cmu.ml.rtw.pra.PraDriver`.  If you have any questions I'm
happy to help (contact me at mg1 at cs dot cmu dot edu).

EMNLP 2013
==========

The code that was used to run the experiments in the EMNLP 2013 paper
(http://rtw.ml.cmu.edu/emnlp2013_pra/) was a slightly earlier version of the
code found in the initial commit of this repository.  I'm pretty sure that the
only substantive thing that changed was the handling of negative evidence
(other things changed, like cleaning up the API and making it faster, but none
that affected the behavior of the algorithm).  In the paper I subsampled the
negative examples to match the number of positive examples.  Now I do something
more fancy, trying to balance the feature weight from the positive and negative
examples.  If you really want to reproduce the results from the paper, you
should be able to fix that pretty easily (in `PraDriver.learnFeatureWeights()`);
I'm pretty confident that the change in handling negative evidence wouldn't
produce any substantial difference in the results of the paper - it might make
the performance of all methods slightly better, but I really doubt it would
change the relative performance of the methods.

All you should need to do to reproduce the results of the paper (after
downloading the two tar files found on rtw.ml.cmu.edu/emnlp2013_pra) is pass
the right parameters into `PraDriver` when invoking it as described above.
`data.tar.gz` gives the data files that you need to use as some of the arguments,
and `run_experiments.py` in `scripts.tar.gz` gives you both the parameters that
constitute the remainder of the arguments and how you should specify them.  You
should be able to change a few paths (and probably uncomment some of the lines,
as directed in the code) and just run `run_experiments.py`, and hopefully it
will work.  I'm quite willing to help you get this running, but until there is
actual demand for exactly reproducing the results as found in the paper, I'll
leave these instructions vague.  If you just want to use the PRA code in some
new application, you shouldn't try to use the scripts I used for this paper;
just look at the documentation in the code.

License
=======

This code makes use of a few libraries that are distributed under the Apache
License and the Common Public License.  Those are in the lib/ directory.  The
code under the src/ directory is distributed under the terms of the GNU General
Public License, version 3 (or, at your choosing, any later version of that
license).  You can find the text of that license at the following location:
http://www.gnu.org/licenses/gpl-3.0.txt.
