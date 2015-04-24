package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

object fix_multilingual_svo {
  val fileUtil = new FileUtil

  val aliasFile = "/home/mg1/data/freebase/freebase-aliases-only.tsv"
  val freebaseFile = "/home/mg1/data/freebase/freebase-just-relation-triples.tsv"
  val originalBase = "/home/mg1/data/multilingual/original/"
  val base = "/home/mg1/data/multilingual/svo/"

  val languages = Seq("arabic", "chinese", "french", "georgian", "hindi", "indonesian", "latvian",
    "russian", "swahili", "tagalog")

  val punctuation = """\p{Punct}+""".r.pattern

  def main(args: Array[String]) {
    //println("Reformatting SVO data")
    //reformat_all_languages()
    println("Finding aliases in the SVO data")
    find_aliases()
    println("Finding alias pairs in the SVO data")
    find_alias_pairs()
    println("Finding MID pairs")
    find_mids()
  }

  def reformat_all_languages() {
    languages.par.foreach(language => {
      println(s"Reformatting ${language} triples")
      val original_file = s"${originalBase}multilingual_relations_data-auto-extractions-${language}.bz2"
      reformat_data(original_file, s"${base}${language}_svo.tsv")
      println(s"Done reformatting ${language} triples")
    })
  }

  def reformat_data(in_file: String, out_file: String) {
    val out = fileUtil.getFileWriter(out_file)
    for (line <- fileUtil.readLinesFromBZ2File(in_file).asScala) {
      val fields = line.split(" \\|\\|\\| ")
      if (fields.size != 9) {
        println(line)
        println(fields)
        for (field <- fields) {
          println(field)
        }
        throw new RuntimeException()
      }
      out.write(fields(2))
      out.write("\t")
      out.write(fields(3))
      out.write("\t")
      out.write(fields(4))
      out.write("\n")
    }
    out.close
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

  def find_aliases() {
    println("Reading SVO files")
    val np_map = languages.par.flatMap(language => {
      fileUtil.readLinesFromFile(s"${base}${language}_svo.tsv").asScala.flatMap(line => {
        val fields = line.split("\t")
        val s_words = fields(0).split(" ").map(x => (x, (fields(0), language)))
        val o_words = fields(2).split(" ").map(x => (x, (fields(2), language)))
        s_words ++ o_words
      })
    }).groupBy(_._1).mapValues(_.map(_._2).seq).seq
    println(s"Found ${np_map.size} phrases to check")
    println("Processing alias file")
    val found = new mutable.HashMap[String, mutable.HashSet[String]]
    var line = ""
    var i = 0
    val reader = fileUtil.getBufferedReader(aliasFile)
    while ({ line = reader.readLine; line != null }) {
      i += 1
      if (i % 1000000 == 0) println(i)
      val alias = line.split("\t").last
      if (!punctuation.matcher(alias).matches && alias.size > 1) {
        if (np_map.contains(alias)) {
          for (np_language <- np_map(alias)) {
            val np = np_language._1
            val language = np_language._2
            val string = s"${np}\tcontains alias\t${alias}"
            found.getOrElseUpdate(language, new mutable.HashSet[String]).add(string)
          }
        }
      }
    }
    println(s"NPs: ${np_map.size}")
    println(s"Found: ${found.size}")
    languages.par.foreach(language => {
      fileUtil.writeLinesToFile(s"${base}found_aliases_${language}.tsv",
        found.getOrElse(language, Set[String]()).toList.sorted.asJava)
    })
  }

  def find_alias_pairs() {
    println("Reading contains alias files")
    val alias_pairs = languages.par.flatMap(language => {
      val np_to_alias = fileUtil.readLinesFromFile(s"${base}found_aliases_${language}.tsv").asScala.map(line => {
        val fields = line.split("\t")
        val np = fields(0)
        val alias = fields(2)
        (np -> alias)
      }).groupBy(_._1).mapValues(_.map(_._2).toSeq)
      fileUtil.readLinesFromFile(s"${base}${language}_svo.tsv").asScala.flatMap(line => {
        val fields = line.split("\t")
        val s = fields(0)
        val o = fields(2)
        if (np_to_alias.contains(s) && np_to_alias.contains(o)) {
          for (s_alias <- np_to_alias(s);
               o_alias <- np_to_alias(o))
             yield ((s_alias, o_alias), language)
        } else {
          Seq()
        }
      })
    }).groupBy(_._1).mapValues(_.map(_._2).seq.toSet).seq
    fileUtil.writeLinesToFile(s"${base}alias_pairs.tsv", alias_pairs.map(x => {
        val l = x._2.mkString(",")
        s"${x._1._1},${x._1._2}\t${l}"
    }).toList.sorted.asJava)
  }

  def find_mids() {
    println("Reading alias file")
    val aliases = new mutable.HashMap[String, mutable.HashSet[String]]
    var line = ""
    var i = 0
    var reader = fileUtil.getBufferedReader(aliasFile)
    while ({ line = reader.readLine; line != null }) {
      i += 1
      if (i % 1000000 == 0) println(i)
      val fields = line.split("\t")
      val mid = fields(0)
      val alias = fields.last
      aliases.getOrElseUpdate(mid, new mutable.HashSet[String]).add(alias)
    }

    println("Reading alias pairs file")
    val alias_pairs = fileUtil.readLinesFromFile(s"${base}alias_pairs.tsv").asScala.map(line => {
      val fields = line.split("\t")
      val aliases = fields(0)
      val langs = fields(1)
      val alias_fields = aliases.split(",")
      val alias1 = alias_fields(0)
      val alias2 = alias_fields(1)
      ((alias1, alias2) -> langs.split(",").toSet)
    }).toMap

    println("Reading freebase file and checking for alias pairs")
    i = 0
    reader = fileUtil.getBufferedReader(freebaseFile)
    val mid_pairs = new mutable.HashMap[(String, String), mutable.HashSet[(String, String)]]
    while ({ line = reader.readLine; line != null }) {
      i += 1
      if (i % 1000000 == 0) println(i)
      val fields = line.split("\t")
      if (fields.size == 3) {
        val mid1 = fields(0)
        val relation = fields(1)
        val mid2 = fields(2)
        val mid1_aliases = aliases.getOrElse(mid1, Set[String]())
        val mid2_aliases = aliases.getOrElse(mid2, Set[String]())
        for (alias1 <- mid1_aliases;
             alias2 <- mid2_aliases) {
          if (alias_pairs.contains((alias1, alias2))) {
            for (language <- alias_pairs((alias1, alias2))) {
              mid_pairs.getOrElseUpdate((language, relation), new mutable.HashSet[(String, String)]()).add((mid1, mid2))
            }
          }
          if (alias_pairs.contains((alias2, alias1))) {
            for (language <- alias_pairs((alias2, alias1))) {
              mid_pairs.getOrElseUpdate((language, relation), new mutable.HashSet[(String, String)]()).add((mid2, mid1))
            }
          }
        }
      }
    }

    println("Outputting results")
    mid_pairs.groupBy(_._1._1).par.foreach(language_results => {
      val language = language_results._1
      val relation_counts = language_results._2.map(entry => {
        val relation = entry._1._2
        val count = entry._2.size
        (relation, count)
      }).toList.sortBy(-_._2)
      fileUtil.writeLinesToFile(s"${base}${language}_found_relations.tsv",
        relation_counts.map(x => s"${x._1}\t${x._2}").asJava)
      language_results._2.foreach(relation_mids => {
        val relation = relation_mids._1._2
        val fixed = relation.replace("/", "_")
        val mids = relation_mids._2
        val dirname = s"${base}${language}_mids/"
        fileUtil.mkdirs(dirname)
        val filename = s"${dirname}${fixed}"
        fileUtil.writeLinesToFile(filename, mids.map(pair => s"${pair._1}\t${pair._2}").toList.asJava)
      })
    })
  }
}
