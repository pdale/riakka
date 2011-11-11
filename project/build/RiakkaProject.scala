import sbt._

class RiakkaProject(info: ProjectInfo) extends DefaultProject(info) {


  val listjson = "net.liftweb" %% "lift-json" % "2.4-M5"
  val dispatch = "net.databinder" %% "dispatch-http" % "0.8.6"
  val dispatch_json = "net.databinder" %% "dispatch-http-json" % "0.8.6"
  val slf4j_api = "org.slf4j" % "slf4j-api" % "1.5.6"
  val slf4j_simple = "org.slf4j" % "slf4j-simple" % "1.5.6"

  val scalatest = "org.scalatest" %% "scalatest" % "1.6.1"

  val codehaus = "codehaus repository" at "http://repository.codehaus.org/"
  val databinder_net = "databinder.net repository" at "http://www.databinder.net/repo"
  val scala_snapshots = "scala snapshots" at "http://scala-tools.org/repo-snapshots/"

}
