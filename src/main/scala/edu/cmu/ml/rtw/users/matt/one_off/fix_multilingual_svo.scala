package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

object fix_multilingual_svo {
  val fileUtil = new FileUtil

  val aliasFile = "/home/mg1/data/freebase/freebase-aliases-only.tsv"
  val base = "/home/mg1/data/multilingual_svo/"

  def reformat_all_languages() {
    for (language <- languages) {
      println(s"Reformatting ${language} triples")
      reformat_data(s"${base}relations_${language}.bz2", s"${base}${language}_svo.tsv")
    }
  }

  def reformat_data(in_file: String, out_file: String) {
    val out = fileUtil.getFileWriter(out_file)
    for (line <- fileUtil.readLinesFromBZ2File(in_file).asScala) {
      val fields = line.split(" \\|\\|\\| ")
      if (fields.size != 4) {
        println(line)
        println(fields)
        for (field <- fields) {
          println(field)
        }
        throw new RuntimeException()
      }
      out.write(fields(1))
      out.write("\t")
      out.write(fields(2))
      out.write("\t")
      out.write(fields(3))
      out.write("\n")
    }
    out.close
  }

  def find_aliases() {
    println("Reading SVO files")
    val np_map = languages.par.flatMap(language => {
      fileUtil.readLinesFromFile(s"${base}${language}_svo.tsv").asScala.flatMap(line => {
        val fields = line.split("\t")
        Seq((fields(0), language), (fields(2), language))
      })
    }).groupBy(_._1).mapValues(_.map(_._2).seq).seq
    println("Processing alias file")
    val reader = fileUtil.getBufferedReader(aliasFile)
    var line = ""
    val found = new collection.mutable.HashSet[String]
    while ({ line = reader.readLine; line != null }) {
      val alias = line.split("\t").last
      if (np_map.contains(alias)) found += alias + "\t" + np_map(alias).mkString(",")
    }
    println(s"NPs: ${np_map.size}")
    println(s"Found: ${found.size}")
    fileUtil.writeLinesToFile(s"${base}found_nps.tsv", found.toList.sorted.asJava)
  }

  def count_vps() {
    println("Reading SVO files")
    languages.par.foreach(language => {
      val vps = fileUtil.readLinesFromFile(s"${base}${language}_svo.tsv").asScala.map(line => {
        val fields = line.split("\t")
        (fields(1), 1)
      }).groupBy(_._1).mapValues(_.map(_._2).sum)
      println(s"${language}: ${vps.size}")
      val top_counts = vps.map(x => (x._2, x._1)).toList.sorted.reverse.take(5)
      println(s"${language} top counts: ${top_counts}")
    })
  }

  val languages = Seq("arabic", "chinese", "french", "georgian", "hindi", "indonesian", "latvian",
    "russian", "swahili", "tagalog")

  def main(args: Array[String]) {
    count_vps()
  }
}
