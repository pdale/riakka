package riakka

import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonAST.{concat,render}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser.{parse,parseOpt}
import net.liftweb.json.Extraction._
import java.io._

object Jiak {
  def init = new Jiak("localhost", 8098, "jiak")
  implicit def enrich_jvalue(value: JValue) = new {
    def to_json = pretty(render(value))
  } // can this be done just by importing riakka._ ?
}

class Jiak(val hostname: String, val port: Int, val jiak_base: String) extends Logging {

  import dispatch._
  private def http = new Http with RiakkaExceptionHandler
  private val db = :/(hostname, port) / jiak_base

  /** Find all element keys of a given bucket and return in a Seq. */
  def find_all(bucket: Symbol): Seq[String] = {
    val response = http(db / bucket.name as_str)
    for { JString(key) <- parse(response) \\ "keys" } yield key
  }

  def get(metadata: %): (%, JObject) = parse(http(db / metadata.id as_str))

  def conditional_get(metadata: %): (%, Option[JObject]) = {
    try {
      val request0 = db / metadata.id
      val request = metadata.vtag match {
        case Some(vtag) => request0 <:< Map("If-None-Match" -> ("\"" + vtag + "\""))
        case None => request0
      }
      (metadata, Some(parse(http(request as_str))._2))
    } catch {
      case NotModified => (metadata, None)
    }
  }

  /** Gets an attachment from raw -- WARNING: only works in Riak trunk **/
  def get_attachment(metadata: %, out: OutputStream): Unit = http(:/(hostname, port) / "raw" / metadata.id >>> out)

  def save_async(metadata: %, obj: JValue) {
    scala.actors.Actor.actor {
      do_save(metadata, obj)
    }
  }

  def save(metadata: %, obj: JValue) {
    do_save(metadata, obj)
  }

  private def do_save(metadata: %, obj: JValue) {
    http((db / metadata.id <:< Map("Content-Type" -> "application/json") <<< tuple_to_json(metadata, obj) >|))
  }

  def save_with_response(metadata: %, obj: JObject): (%, JObject) = {
    val handler = db / metadata.id <:< Map("Content-Type" -> "application/json") <<? Map("returnbody" -> "true") <<< tuple_to_json(metadata, obj)
    val response = http(handler as_str)
    parse(response)
  }

  /** Saves an attachment to raw -- WARNING: only works in Riak trunk **/
  def save_attachment(metadata: %, file: File, content_type: String) {
    do_save_attachment(metadata, file, content_type)
  }

  def save_attachment_async(metadata: %, file: File, content_type: String) {
    scala.actors.Actor.actor {
      do_save_attachment(metadata, file, content_type)
    }
  }

  private def do_save_attachment(metadata: %, file: File, content_type: String) {
    http(:/(hostname, port) / "raw" / metadata.id <<< (file, content_type) >|)
  }

  def delete(metadata: %): Unit = http((db / metadata.id DELETE) >|)

  def walk(metadata: %, specs: ^^ *): Seq[(%, JObject)] = {
    val response = http(db / metadata.id / specs.mkString("/") as_str)
    val json = parse(response)
    val JField(_, JArray(List(JArray(riak_objects)))) = (json \ "results")
    for (riak_object <- riak_objects) yield jvalue_to_tuple(riak_object)
  }

  /** Local implicit functions */

  private implicit def tuple_to_json(metadata: %, obj: JValue): String = {
    implicit val formats = Serialization.formats(NoTypeHints)

    val m = decompose(metadata) map {
      case JObject(JField("tag", t) :: JField("key", k) :: JField("bucket", b) :: Nil) => {
        val elems = Map("bucket" -> b, "key" -> k, "tag" -> t)
        JArray(elems("bucket") :: elems("key") :: elems("tag") :: Nil)
      }
      case x => x
    }

    val riak_object = m merge JObject(JField("object", obj) :: Nil)
    debug("Sending to server\n" + pretty(render(riak_object)))
    compact(render(riak_object))
  }

  private implicit def jvalue_to_tuple(json: JValue): (%, JObject) = {
    debug("Receiving from server\n" + pretty(render(json)))
    implicit val formats = new DefaultFormats {
        override def dateFormatter = new java.text.SimpleDateFormat("E, dd MMM yyyy HH:mm:ss ZZZ")
      }

    val json2 = json map {
        case JField("links", arr) => arr map {
          case JArray(JString(b) :: JString(k) :: JString(t) :: Nil) => ("bucket" -> b) ~ ("key" -> k) ~ ("tag" -> t)
          case x => x
        }
        case x => x
      }

    val metadata = json2.extract[%]
    val JField(_, JObject(obj)) = json \ "object"
    (metadata, obj)
  }

}
