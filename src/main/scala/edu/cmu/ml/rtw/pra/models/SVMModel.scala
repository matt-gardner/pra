package edu.cmu.ml.rtw.pra.models

import cc.mallet.pipe.Noop
import cc.mallet.pipe.Pipe
import cc.mallet.pipe.Target2Label
import cc.mallet.pipe.SerialPipes
import cc.mallet.types.Alphabet
import cc.mallet.types.FeatureVector
import cc.mallet.types.Instance
import cc.mallet.types.InstanceList

import edu.cmu.ml.rtw.pra.config.PraConfig
import edu.cmu.ml.rtw.pra.experiments.Dataset
import edu.cmu.ml.rtw.pra.features.FeatureMatrix
import edu.cmu.ml.rtw.pra.features.MatrixRow
import edu.cmu.ml.rtw.pra.features.PathType
import edu.cmu.ml.rtw.pra.features.PathTypeFactory
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

import edu.cmu.ml.rtw.pra.mallet_svm.kernel.LinearKernel
import edu.cmu.ml.rtw.pra.mallet_svm.SVMClassifierTrainer
import edu.cmu.ml.rtw.pra.mallet_svm.SVMClassifier
import edu.cmu.ml.rtw.pra.mallet_svm.libsvm.svm_model
import edu.cmu.ml.rtw.pra.mallet_svm.libsvm.svm_parameter

class SVMModel(config: PraConfig, l1Weight: Double, l2Weight: Double, binarizeFeatures: Boolean)
      extends PraModel{
  
  // initializes to an empty sequence
  var svmClassifier: SVMClassifier = _
  var alphabet: Alphabet = _
  
  var lrWeights: Seq[Double] = Seq()
  
  /**
   * Given a feature matrix and a list of sources and targets that determines whether an
   * instances is positive or negative, train an SVM.
   */
  def trainModel(featureMatrix: FeatureMatrix, dataset: Dataset, featureNames: Seq[String]) = {
    println("Learning feature weights")
    println("Prepping training data")
    val knownPositives = dataset.getPositiveInstances.asScala.map(x => (x.getLeft.toInt, x.getRight.toInt)).toSet
    val knownNegatives = dataset.getNegativeInstances.asScala.map(x => (x.getLeft.toInt, x.getRight.toInt)).toSet

    println("Separating into positive, negative, unseen")
    val grouped = featureMatrix.getRows().asScala.groupBy(row => {
      val sourceTarget = (row.sourceNode.toInt, row.targetNode.toInt)
      if (knownPositives.contains(sourceTarget))
        "positive"
      else if (knownNegatives.contains(sourceTarget))
        "negative"
      else
        "unseen"
    })
    val positiveMatrix = new FeatureMatrix(grouped.getOrElse("positive", Seq()).asJava)
    val negativeMatrix = new FeatureMatrix(grouped.getOrElse("negative", Seq()).asJava)
    val unseenMatrix = new FeatureMatrix(grouped.getOrElse("unseen", Seq()).asJava)
    if (config.outputMatrices && config.outputBase != null) {
      println("Outputting matrices")
      val base = config.outputBase
      config.outputter.outputFeatureMatrix(s"${base}positive_matrix.tsv", positiveMatrix, featureNames.asJava)
      config.outputter.outputFeatureMatrix(s"${base}negative_matrix.tsv", negativeMatrix, featureNames.asJava)
      config.outputter.outputFeatureMatrix(s"${base}unseen_matrix.tsv", unseenMatrix, featureNames.asJava)
    }

    println("Creating alphabet")
    
    // Does this just give Alphabet dataAlphabet
    // do we also need LabelAlphabet labelAlphabet
    // TODO : Use your own alphabet ?? for Target2Label
    
    // Set up some mallet boiler plate so we can use Burr's ShellClassifier
    //val pipe = new Noop()
    // set up a target to label pipe for the svm classifier
    // the instanceList gets doubles as labels
    
    val pipes = new mutable.ArrayBuffer[Pipe]
    pipes.+=(new Noop())
    pipes.+=(new Target2Label())
    val pipe = new SerialPipes(pipes.asJava)
    val data = new InstanceList(pipe)
    alphabet = new Alphabet(featureNames.asJava.toArray())
    
    println("Converting positive matrix to MALLET instances and adding to the dataset")
    // First convert the positive matrix to a scala object
    positiveMatrix.getRows().asScala
    // Then, in parallel, map the MatrixRow objects there to MALLET Instance objects
      .par.map(row => matrixRowToInstance(row, alphabet, true))
    // Then, sequentially, add them to the data object, and simultaneously count how many columns
    // there are.
      .seq.foreach(instance => {
        data.addThruPipe(instance)
      })
    println("Adding negative evidence")
    addNegativeEvidence(positiveMatrix.size,
                        positiveMatrix.getRows().asScala.map(_.columns).sum,
                        negativeMatrix,
                        unseenMatrix,
                        data,
                        alphabet)
                        
    /* This LR part is redundant shit */
    /*                       
    println("Creating an LR object for non-zero feature weights to generate test matrix ")
    val lr = new MalletLogisticRegression(alphabet)
    if (l2Weight != 0.0) {
      println("Setting L2 weight to " + l2Weight)
      lr.setL2wt(l2Weight)
    }
    if (l1Weight != 0.0) {
      println("Setting L1 weight to " + l1Weight)
      lr.setL1wt(l1Weight)
    }
    lr.train(data)
    val features = lr.getSparseFeatures()
    val params = lr.getSparseParams()
    val bias = lr.getBias()
    val weights = new mutable.ArrayBuffer[Double]()
    var j = 0
    for (i <- 0 until featureNames.size) {
      if (j >= features.length) {
        weights += 0.0
      } else if (features(j) > i) {
        weights += 0.0
      } else if (features(j) == i) {
        weights += params(j)
        j += 1
      }
    }
    println("Outputting feature weights")
    if (config.outputBase != null) {
      val javaWeights = weights.map(java.lang.Double.valueOf).asJava
      config.outputter.outputWeights(config.outputBase + "weights.tsv", javaWeights, featureNames.asJava)
    }
    lrWeights = weights.toSeq
    */
    
    println("Creating the MalletLibSVM object")
    //val lr = new MalletLogisticRegression(alphabet)
    val svmTrainer = new SVMClassifierTrainer(new LinearKernel())
    /*
    if (l2Weight != 0.0) {
      println("Setting L2 weight to " + l2Weight)
      lr.setL2wt(l2Weight)
    }
    if (l1Weight != 0.0) {
      println("Setting L1 weight to " + l1Weight)
      lr.setL1wt(l1Weight)
    }*/

    // Finally, we train.  All that prep and everything that follows is really just to get
    // ready for and pass on the output of this one line.
    svmClassifier = svmTrainer.train(data)
    
        /*    
        val model = svmClassifier.getModel()
        System.out.println("number of classes in svm model is " + model.nr_class);
        if(model.param.svm_type == svm_parameter.ONE_CLASS)
          println("SVM type is ONE_CLASS")
        else if(model.param.svm_type == svm_parameter.EPSILON_SVR)
          println("SVM type is EPSILON_SVR");
        else if(model.param.svm_type == svm_parameter.NU_SVR)
          println("SVM type is NU_SVR");
        else if(model.param.svm_type == svm_parameter.NU_SVC)  // one vs one classification
          println("SVM type is NU_SVC");
        else if(model.param.svm_type == svm_parameter.C_SVC)   // one vs one classification
          println("SVM type is C_SVC");
        else
          println("SVM type is unknown");
        */

  }
  
  // return an empty sequence for svm parameters
  // ideally we should return the alpha parameters
  def getParams(): Seq[Double] = Seq()

  // TODO(matt): Clean up these three methods.  Probably the right thing to do is to put some
  // kind of class into the PraConfig object that lets you decide how to handle negative
  // evidence.
  def addNegativeEvidence(
      numPositiveExamples: Int,
      numPositiveFeatures: Int,
      negativeMatrix: FeatureMatrix,
      unseenMatrix: FeatureMatrix,
      data: InstanceList,
      alphabet: Alphabet) {
    //sampleUnseenExamples(numPositiveExamples, negativeMatrix, unseenMatrix, data, alphabet)
    weightUnseenExamples(numPositiveFeatures, negativeMatrix, unseenMatrix, data, alphabet)
  }

  def sampleUnseenExamples(
      numPositiveExamples: Int,
      negativeMatrix: FeatureMatrix,
      unseenMatrix: FeatureMatrix,
      data: InstanceList,
      alphabet: Alphabet) {
    unseenMatrix.shuffle()
    for (i <- 0 until numPositiveExamples) {
      data.addThruPipe(matrixRowToInstance(unseenMatrix.getRow(i), alphabet, false))
    }
  }

  def weightUnseenExamples(
      numPositiveFeatures: Int,
      negativeMatrix: FeatureMatrix,
      unseenMatrix: FeatureMatrix,
      data: InstanceList,
      alphabet: Alphabet) {
    var numNegativeFeatures = 0
    for (negativeExample <- negativeMatrix.getRows().asScala) {
      numNegativeFeatures += negativeExample.columns
      data.addThruPipe(matrixRowToInstance(negativeExample, alphabet, false))
    }
    println("Number of positive features: " + numPositiveFeatures)
    println("Number of negative features: " + numNegativeFeatures)
    if (numNegativeFeatures < numPositiveFeatures) {
      println("Using unseen examples to make up the difference")
      val difference = numPositiveFeatures - numNegativeFeatures
      var numUnseenFeatures = 0.0
      println("Number of unseen matrix rows: " + unseenMatrix.size())
      for (unseenExample <- unseenMatrix.getRows().asScala) {
        numUnseenFeatures += unseenExample.columns
      }
      println("Number of unseen features: " + numUnseenFeatures)
      val unseenWeight = difference / numUnseenFeatures
      println("Unseen weight: " + unseenWeight)
      for (unseenExample <- unseenMatrix.getRows().asScala) {
        val unseenInstance = matrixRowToInstance(unseenExample, alphabet, false)
        data.addThruPipe(unseenInstance)
        data.setInstanceWeight(unseenInstance, unseenWeight)
      }
    }
  }

  /**
   * Give a score to every row in the feature matrix, according to the learned parameters and
   * support vectors.
   *
   * This just applies the SVM function to the feature
   * matrix, returning a score for each row in the matrix.  We convert the matrix into a map,
   * keyed by source node, to facilitate easy ranking of predictions for each source.  The lists
   * returned are not sorted yet, however.
   *
   * @param featureMatrix A feature matrix specified as a list of {@link MatrixRow} objects.
   *     Each row receives a score from the SVM function.
   * @param weights This is not used as of now 
   *
   * @return A map from source node to (target node, score) pairs, where the score is computed
   *     from the features in the feature matrix and the supplied weights.
   */
  def classifyInstances(featureMatrix: FeatureMatrix, weights: Seq[Double]): Map[Int, Seq[(Int, Double)]] = {
    val sourceScores = new mutable.HashMap[Int, mutable.ArrayBuffer[(Int, Double)]]
    for (row <- featureMatrix.getRows().asScala) {
      val score = classifyMatrixRow(row, weights)
      println("received score is " + score)
      sourceScores.getOrElseUpdate(row.sourceNode, new mutable.ArrayBuffer[(Int, Double)])
        .append((row.targetNode, score))
    }
    sourceScores.mapValues(_.toSeq.sortBy(x => (-x._2, x._1))).toMap
  }

  /**
   * File must be in the format "%s\t%f\n", where the string is a path description.  If the model
   * is output by outputWeights, you should be fine.
   */
  def readWeightsFromFile(filename: String, pathTypeFactory: PathTypeFactory): Seq[(PathType, Double)] = {
    val lines = new FileUtil().readLinesFromFile(filename).asScala
    lines.map(line => {
      val fields = line.split("\t")
      val description = fields(0)
      val weight = fields(1).toDouble
      val pathType = pathTypeFactory.fromString(description)
      (pathType, weight)
    })
  }

  
  /**
   * Compute score for matrix row according to learned parameters and support vectors
   * which are stored in the svmClassifier
   */
  def classifyMatrixRow(row: MatrixRow, weights: Seq[Double]) = {
    svmClassifier.scoreInstance(matrixRowToInstance(row, alphabet, true))
  }


  def matrixRowToInstance(row: MatrixRow, alphabet: Alphabet, positive: Boolean): Instance = {
    val value = if (positive) 1.0 else 0.0
    val rowValues = row.values.map(v => if (binarizeFeatures) 1 else v)
    val feature_vector = new FeatureVector(alphabet, row.pathTypes, rowValues)
    new Instance(feature_vector, value, row.sourceNode + " " + row.targetNode, null)
  }

}