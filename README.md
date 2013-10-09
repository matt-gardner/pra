PRA (Path Ranking Algorithm)
============================

An efficient implementation of Ni Lao's Path Ranking Algorithm (PRA), using GraphChi, a library for efficient processing of large graphs on a single machine.  This also has the beginnings of my own improvements to the algorithm, but there is more to come of that later.

The code is pretty well documented, I believe, and so I'm not going to put much here.  Just compile it with ant (`ant compile`), and run it with `java -cp lib/*:classes/ edu.cmu.ml.rtw.pra.PraDriver`.  If you have any questions I'm happy to help.

EMNLP 2013
==========

How to reproduce the results for the EMNLP 2013 paper (after downloading and compile the code; I'm also assuming in these instructions that you're familiar enough with computers to know how to change a path if you get a file not found error):

1. Download the graph files from .... (TODO)
2. Run the command found in the tar file .... (TODO)

License
=======

This code makes use of a few libraries that are distributed under the Apache License and the Common Public License.  Those are in the lib/ directory.  The code under the src/ directory is distributed under the terms of the GNU General Public License, version 3 (or, at your choosing, any later version of that license).  You can find the text of that license at the following location: http://www.gnu.org/licenses/gpl-3.0.txt.
