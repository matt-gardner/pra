package edu.cmu.ml.rtw.users.matt.one_off

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

object fix_arvinds_split {

  val relations = Seq(
    "_architecture_structure_address",
    "_aviation_airport_serves",
    "_book_book_characters",
    "_book_literary_series_works_in_this_series",
    "_book_written_work_original_language",
    "_broadcast_content_artist",
    "_broadcast_content_genre",
    "_business_industry_companies",
    "_cvg_computer_videogame_cvg_genre",
    "_cvg_game_version_game",
    "_cvg_game_version_platform",
    "_education_educational_institution_campuses",
    "_education_educational_institution_school_type",
    "_film_film_cinematography",
    "_film_film_country",
    "_film_film_directed_by",
    "_film_film_film_festivals",
    "_film_film_language",
    "_film_film_music",
    "_film_film_rating",
    "_film_film_sequel",
    "_geography_river_cities",
    "_geography_river_mouth",
    "_location_location_contains",
    "_music_album_genre",
    "_music_artist_genre",
    "_music_artist_label",
    "_music_artist_origin",
    "_music_composition_composer",
    "_music_composition_lyricist",
    "_music_genre_albums",
    "_organization_organization_founders",
    "_organization_organization_locations",
    "_organization_organization_sectors",
    "_people_deceased_person_cause_of_death",
    "_people_deceased_person_place_of_death",
    "_people_ethnicity_people",
    "_people_family_members",
    "_people_person_nationality",
    "_people_person_place_of_birth",
    "_people_person_profession",
    "_people_person_religion",
    "_soccer_football_player_position_s",
    "_time_event_locations",
    "_tv_tv_program_country_of_origin",
    "_tv_tv_program_genre"
  )

  def NOT_main(args: Array[String]) {
    val fileUtil = new FileUtil
    val base_dir = "/home/mg1/data/freebase/arvind/"
    val split_dir = "/home/mg1/pra/splits/freebase_arvind/"
    val dev_split_dir = "/home/mg1/pra/splits/freebase_arvind_dev/"
    val pos = "positive_matrix.tsv.translated"
    val neg = "negative_matrix.tsv.translated"
    val dev = "dev_matrix.tsv.translated"
    val test = "test_matrix.tsv.translated"
    relations.par.foreach(relation => {
      println(s"Processing relation $relation")
      val to_read_dir = base_dir + relation + "/"
      val relation_dir = split_dir + relation + "/"
      val dev_relation_dir = dev_split_dir + relation + "/"
      fileUtil.mkdirs(relation_dir)
      fileUtil.mkdirs(dev_relation_dir)
      val training_lines = fileUtil.readLinesFromFile(to_read_dir + pos).asScala.map(line => {
          val fields = line.split("\t")
          fields.take(2).mkString("\t") + "\t1"
        }) ++
        fileUtil.readLinesFromFile(to_read_dir + neg).asScala.map(line => {
          val fields = line.split("\t")
          fields.take(2).mkString("\t") + "\t-1"
        })
      val dev_lines = fileUtil.readLinesFromFile(to_read_dir + dev).asScala.map(line => {
        val fields = line.split("\t")
        fields.take(2).mkString("\t") + "\t" + fields.last
      })
      val testing_lines = fileUtil.readLinesFromFile(to_read_dir + test).asScala.map(line => {
        val fields = line.split("\t")
        fields.take(2).mkString("\t") + "\t" + fields.last
      })
      fileUtil.writeLinesToFile(dev_relation_dir + "training.tsv", training_lines.asJava)
      fileUtil.writeLinesToFile(dev_relation_dir + "testing.tsv", dev_lines.asJava)
      fileUtil.writeLinesToFile(relation_dir + "training.tsv", training_lines.asJava)
      fileUtil.writeLinesToFile(relation_dir + "testing.tsv", testing_lines.asJava)
      println(s"Done processing relation $relation")
    })
    fileUtil.writeLinesToFile(split_dir + "relations_to_run.tsv", relations.asJava)
    fileUtil.writeLinesToFile(dev_split_dir + "relations_to_run.tsv", relations.asJava)
  }
}
