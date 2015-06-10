package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

object multilingual_svo_experiments {
  val fileUtil = new FileUtil

  val aliasFile = "/home/mg1/data/freebase/freebase-aliases-only.tsv"
  val freebaseFile = "/home/mg1/data/freebase/freebase-just-relation-triples.tsv"
  val originalBase = "/home/mg1/data/multilingual/original/"
  val base = "/home/mg1/data/multilingual/svo/"
  val translationBase = "/home/mg1/data/multilingual/translation/"

  val languages = Seq("arabic", "chinese", "french", "georgian", "hindi", "indonesian", "latvian",
    "russian", "swahili", "tagalog")

  val punctuation = """\p{Punct}+""".r.pattern
  val relations = Seq(
    "/location/location/contains",
    "/people/person/nationality",
    "/music/album/artist",
    "/film/film/country",
    "/location/administrative_division/country",
    "/book/written_work/subjects",
    "/tv/tv_program/country_of_origin",
    "/biology/organism_classification/higher_classification",
    "/language/human_language/main_country",
    "/language/human_language/region",
    "/people/ethnicity/languages_spoken"
  )
  val split_base = "/home/mg1/pra/splits/multilingual/all_languages/"
  val better_split_base = "/home/mg1/pra/splits/multilingual/better/"
  val french_split_base = "/home/mg1/pra/splits/multilingual/french_test/"
  val percent_training = .8
  val experiment_spec_base = "/home/mg1/pra/experiment_specs/multilingual/better/"

  def main(args: Array[String]) {
    //println("Reformatting SVO data")
    //reformat_all_languages()
    //println("Finding aliases in the SVO data")
    //find_aliases()
    //println("Finding alias pairs in the SVO data")
    //find_alias_pairs()
    //println("Finding MID pairs")
    //find_mids()
    //println("Creating split")
    //create_split()
    //create_french_test_split()

    //create_relation_sets()

    //filter_aliases()
    //count_aliases()
    create_better_split()
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
      val v = fileUtil.readLinesFromFile(s"${base}${language}_svo.tsv").asScala.flatMap(line => {
        val fields = line.split("\t")
        val s_words = fields(0).split(" ").map(x => (x, (fields(0), language)))
        val o_words = fields(2).split(" ").map(x => (x, (fields(2), language)))
        s_words ++ o_words
      })
      println(s"Done reading ${language}")
      v
    }).groupBy(_._1).mapValues(_.map(_._2).seq).seq
    println(s"Found ${np_map.size} phrases to check")
    val found = new mutable.HashMap[String, mutable.HashSet[String]]
    println("Reading alias file into memory")
    val lines = fileUtil.readLinesFromFile(aliasFile).asScala
    var i = new java.util.concurrent.atomic.AtomicInteger
    println("Processing alias file lines")
    lines.par.foreach(line => {
      if (i.getAndIncrement % 100000 == 0) println(s"${i.get}: ${found.size}")
      val alias = line.split("\t").last
      for (alias_word <- alias.split(" ")) {
        if (!punctuation.matcher(alias_word).matches && alias_word.size > 1) {
          if (np_map.contains(alias_word)) {
            for (np_language <- np_map(alias_word)) {
              val np = np_language._1
              if (np.contains(alias)) {
                val language = np_language._2
                val string = s"${np}\tcontains alias\t${alias}"
                found.getOrElseUpdate(language, new mutable.HashSet[String]).add(string)
              }
            }
          }
        }
      }
    })
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
              mid_pairs.getOrElseUpdate((language, relation), new mutable.HashSet[(String, String)]()).add((mid1, mid2))
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

  def create_split() {
    val relation_instances = relations.par.map(relation => {
      val fixed = relation.replace("/", "_")
      val instances = languages.flatMap(language => {
        val mid_file = s"${base}${language}_mids/${fixed}"
        if (fileUtil.fileExists(mid_file))
          fileUtil.readLinesFromFile(mid_file).asScala
        else
          Seq()
      }).toSet
    (relation -> instances)
    }).seq.toMap
    val random = new scala.util.Random()
    fileUtil.mkdirs(split_base)
    relation_instances.par.foreach(entry => {
      val relation = entry._1
      val instances = entry._2
      val relation_dir = split_base + relation.replace("/", "_") + "/"
      fileUtil.mkdirs(relation_dir)
      val all_file = relation_dir + "all.tsv"
      val shuffled = random.shuffle(instances.toList).take(2000)
      val num_training = (shuffled.size * percent_training).toInt
      val training = shuffled.take(num_training)
      val testing = shuffled.drop(num_training)
      fileUtil.writeLinesToFile(s"${relation_dir}training.tsv", training.asJava)
      fileUtil.writeLinesToFile(s"${relation_dir}testing.tsv", testing.asJava)
      fileUtil.writeLinesToFile(s"${relation_dir}all.tsv", shuffled.asJava)
    })
  }

  def create_better_split() {
    val random = new scala.util.Random()
    val language_instances = languages.par.map(language => {
      val relation_instances = relations.map(relation => {
        val fixed = relation.replace("/", "_")
        val mid_file = s"${base}${language}_mids/${fixed}"
        if (fileUtil.fileExists(mid_file)) {
          val instances = fileUtil.readLinesFromFile(mid_file).asScala
          val shuffled = random.shuffle(instances.toList).take(2000)
          val num_training = (shuffled.size * percent_training).toInt
          val training = shuffled.take(num_training)
          val testing = shuffled.drop(num_training)
          (relation -> (training, testing))
        }
        else {
          (relation -> (Seq(), Seq()))
        }
      }).toMap
      (language -> relation_instances)
    }).seq.toMap
    val all_training = relations.par.map(relation => {
      val instances = languages.flatMap(language => {
        language_instances(language)(relation)._1
      })
      (relation -> instances)
    }).toMap
    language_instances.par.foreach(entry => {
      val language = entry._1
      val single_language_split_base = better_split_base + language + "/single_language/"
      fileUtil.mkdirs(single_language_split_base)
      fileUtil.writeLinesToFile(s"${single_language_split_base}relations_to_run.tsv", relations.asJava)
      entry._2.foreach(relation_instances => {
        val relation = relation_instances._1
        val training = relation_instances._2._1
        val testing = relation_instances._2._2
        val relation_dir = single_language_split_base + relation.replace("/", "_") + "/"
        fileUtil.mkdirs(relation_dir)
        fileUtil.writeLinesToFile(s"${relation_dir}training.tsv", training.asJava)
        fileUtil.writeLinesToFile(s"${relation_dir}testing.tsv", testing.asJava)
      })
      create_spec_file("svo", language, "single_language")
      create_spec_file("kb", language, "single_language")
      create_spec_file("kb_svo", language, "single_language")
      create_spec_file("all_svo", language, "single_language")
      create_spec_file("kb_all_svo", language, "single_language")
      val all_language_split_base = better_split_base + language + "/all_languages/"
      fileUtil.mkdirs(all_language_split_base)
      fileUtil.writeLinesToFile(s"${all_language_split_base}relations_to_run.tsv", relations.asJava)
      entry._2.foreach(relation_instances => {
        val relation = relation_instances._1
        val testing = relation_instances._2._2
        val training = (all_training(relation).toSet -- testing).toList
        val relation_dir = all_language_split_base + relation.replace("/", "_") + "/"
        fileUtil.mkdirs(relation_dir)
        fileUtil.writeLinesToFile(s"${relation_dir}training.tsv", training.asJava)
        fileUtil.writeLinesToFile(s"${relation_dir}testing.tsv", testing.asJava)
      })
      create_spec_file("svo", language, "all_languages")
      create_spec_file("kb", language, "all_languages")
      create_spec_file("kb_svo", language, "all_languages")
      create_spec_file("all_svo", language, "all_languages")
      create_spec_file("kb_all_svo", language, "all_languages")
    })
  }

  def get_language_relation_sets(language: String) = {
    s""""load relation_sets/multilingual/${language}", "load relation_sets/multilingual/${language}_contains_alias""""
  }

  def get_all_language_relation_sets() = {
    languages.map(get_language_relation_sets).mkString(",\n")
  }

  def get_split_spec(language: String, split_name: String) = {
    s"""{
    |    "name": "multilingual/better/$language/${split_name}_with_negatives",
    |    "type": "add negatives to split",
    |    "relation metadata": "freebase",
    |    "graph": "multilingual/better/kb_all_svo",
    |    "percent training": 0.8,
    |    "from split": "multilingual/better/$language/${split_name}",
    |    "negative instances": {
    |       "negative to positive ratio": 4
    |    }
    |  }""".stripMargin
  }

  def create_spec_file(name: String, language: String, split: String) = {
    val spec_file_dir = experiment_spec_base + language + "/" + split + "/"
    fileUtil.mkdirs(spec_file_dir)
    val spec_file_name = experiment_spec_base + language + "/" + split + "/" + name + ".json"
    val relation_sets = name match {
      case "kb" => "" // relation_sets isn't read in this case.
      case "svo" => "\"load relation_sets/freebase/kb_aliases_only\",\n" + get_language_relation_sets(language)
      case "kb_svo" => "\"load relation_sets/freebase/kb\",\n" + get_language_relation_sets(language)
      case "all_svo" => "\"load relation_sets/freebase/kb_aliases_only\",\n" + get_all_language_relation_sets()
      case "kb_all_svo" => "\"load relation_sets/freebase/kb\",\n" + get_all_language_relation_sets()
    }
    val graph_params = name match {
      case "kb" => "\"freebase/kb\","
      case all if all.contains("all") =>
        s"""{
        |    "name": "multilingual/better/$name",
        |    "relation sets": [
        |      $relation_sets
        |    ],
        |    "create matrices": false,
        |    "deduplicateEdges": true
        |  },""".stripMargin
      case other => {
        s"""{
        |    "name": "multilingual/better/$language/$name",
        |    "relation sets": [
        |      $relation_sets
        |    ],
        |    "create matrices": false,
        |    "deduplicateEdges": true
        |  },""".stripMargin
      }
    }
    val split_spec = get_split_spec(language, split)
    val spec_contents = s"""load default_pra_parameters
      |{
      |  "graph": $graph_params
      |  "relation metadata": "freebase",
      |  "split": $split_spec,
      |  "pra parameters": {
      |    "features": {
      |      "path follower": {
      |        "matrix accept policy": "paired-targets-only"
      |      }
      |    }
      |  }
      |}""".stripMargin
    fileUtil.writeContentsToFile(spec_file_name, spec_contents)
  }

  def create_french_test_split() {
    val relation_not_french_instances = relations.par.map(relation => {
      val fixed = relation.replace("/", "_")
      val instances = (languages.toSet - "french").flatMap(language => {
        val mid_file = s"${base}${language}_mids/${fixed}"
        if (fileUtil.fileExists(mid_file))
          fileUtil.readLinesFromFile(mid_file).asScala
        else
          Seq()
      }).toSet
    (relation -> instances)
    }).seq.toMap
    val relation_french_instances = relations.par.map(relation => {
      val fixed = relation.replace("/", "_")
      val mid_file = s"${base}french_mids/${fixed}"
      val instances = {
        if (fileUtil.fileExists(mid_file))
          fileUtil.readLinesFromFile(mid_file).asScala
        else
          Seq()
      }
      (relation -> instances)
    }).seq.toMap

    val relation_instances = relations.map(relation => {
      val french = relation_french_instances.getOrElse(relation, Seq())
      val not_french = relation_not_french_instances.getOrElse(relation, Seq())
      (relation -> (not_french, french))
    })
    fileUtil.mkdirs(french_split_base)
    val used_relations = relation_instances.par.flatMap(entry => {
      val relation = entry._1
      val train_instances = entry._2._1
      val test_instances = entry._2._2
      if (test_instances.size > 0) {
        val relation_dir = french_split_base + relation.replace("/", "_") + "/"
        fileUtil.mkdirs(relation_dir)
        fileUtil.writeLinesToFile(s"${relation_dir}training.tsv", train_instances.toList.asJava)
        fileUtil.writeLinesToFile(s"${relation_dir}testing.tsv", test_instances.asJava)
        Seq(relation)
      } else {
        Seq()
      }
    }).seq.toList
    fileUtil.writeLinesToFile(s"${french_split_base}relations_to_run.tsv", used_relations.asJava)
  }

  def filter_aliases() {
    println("Filtering alias files")
    languages.par.foreach(language => {
      println(s"Reading ${language} alias file")
      val lines = fileUtil.readLinesFromFile(s"${base}found_aliases_${language}.tsv").asScala
      println(s"Filtering ${language} alias file")
      val filtered = lines.par.filter(shouldKeepLine).seq
      println(s"Kept ${filtered.size} out of ${lines.size} NP aliases")
      println(s"Writing filtered ${language} alias file to disk")
      fileUtil.writeLinesToFile(s"${base}filtered_aliases_${language}.tsv", filtered.asJava)
    })
  }

  def count_aliases() {
    println("Counting aliases in alias files")
    languages.par.foreach(language => {
      println(s"Reading ${language} alias file")
      val lines = fileUtil.readLinesFromFile(s"${base}filtered_aliases_${language}.tsv").asScala
      val alias_map = lines.par.map(_.split("\t").last).groupBy(alias => alias).mapValues(_.size)
      val sorted = alias_map.toList.sortBy(entry => (-entry._2, entry._1))
      println(s"\n\nTop aliases in ${language}")
      for (entry <- sorted.take(40)) {
        println(s"${entry._1}: ${entry._2}")
      }
    })
  }

  val stopWordAliases = Set("et", "le", "La", "de", "les", "la", "est", "sh", "non", "par", "pour",
    "son", "In", "en", "tu", "ai", "je", "To", "One", "You", "Day", "II", "of", "os", "Ai", "The",
    "the", "un", "'s", "in", "sa", "of", "na", "wa", "za", "au", "wake", "ni", "kati", "pia",
    "maji", "kila", "Ang", "ng", "Le", "Les", "plus", "En", "ce", "il", "se", "premier", "autres",
    "Un", "Une", "Il", "yang", "dan", "di", "ke", "orang", "wilayah", "nama", "para", "Si", "at",
    "si", "ay", "may", "pang", "tao", "San", "Sa", "no", "pag", "and", "uri", "mas", "pa", "Ce",
    "De", "Son", "Des", "Ces", "ne", "grand", "autre", "titre", "pays", "elle", "pas", "fils",
    "fin", "vie", "nombre", "Au", "ar", "uz", "ir", "pie", "to", "kuru", "kara", "ko", "bet",
    "savas", "kuri", "ap", "viens", "tam", "Par", "pat", "Di", "tim", "salah", "paling", "ia",
    "jalan", "kali")
  def shouldKeepLine(line: String): Boolean = {
    val fields = line.split("\t")
    val alias = fields.last
    if (stopWordAliases.contains(alias)) return false
    val np = fields(0)
  val index = np.indexOf(alias)
  if (index > 0 && np(index - 1) != ' ') return false
  if (index + alias.size < np.size && np(index + alias.size) != ' ') return false
  return true
  }

  def create_relation_sets() {
    val file_types = Seq(
      ("%s_contains_alias.json", "found_aliases_%s.tsv"),
    ("%s.json", "%s_svo.tsv")
  )
    val relset_base = "/home/mg1/pra/param_files/relation_sets/multilingual/"
    val template = """{
      |  "relation file": "%s",
      |  "is kb": false
    |}""".stripMargin
    for (language <- languages) {
      for (file_type <- file_types) {
        val relset_file = relset_base + file_type._1.format(language)
        val relation_file = base + file_type._2.format(language)
        val contents = template.format(relation_file)
        fileUtil.writeLinesToFile(relset_file, contents.split("\n").toList.asJava)
      }
    }

    val single_relation_template = """{
      |  "relation file": "%s",
      |  "is kb": false,
      |  "replace relations with": "@SVO@"
    |}""".stripMargin
    for (language <- languages) {
      val file_type = ("%s_single_relation.json", "%s_svo.tsv")
      val relset_file = relset_base + file_type._1.format(language)
      val relation_file = base + file_type._2.format(language)
      val contents = single_relation_template.format(relation_file)
      fileUtil.writeLinesToFile(relset_file, contents.split("\n").toList.asJava)
    }

    val language_pairs = Seq(
      "english-french.txt",
      "english-indonesian.txt",
      "english-swahili.txt",
      "english-tagalog.txt",
      "en-zh.tsv",
      "french-indonesian.txt",
      "french-swahili.txt",
      "french-tagalog.txt",
      "indonesian-swahili.txt",
      "ru-zh.tsv"
    )
    for (pair <- language_pairs) {
      val relset_file = relset_base + pair + ".json"
      val relation_file = translationBase + pair
      val contents = template.format(relation_file)
      fileUtil.writeLinesToFile(relset_file, contents.split("\n").toList.asJava)
    }
  }
}
