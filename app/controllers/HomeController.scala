package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import java.nio.file.{Files, Paths}
import scala.io.Source
import java.io.File
import scala.collection.mutable.ListBuffer
import play.api.libs.json.Json
import scala.util.parsing.json.JSONArray
import play.api.libs.json.JsArray
import play.api.libs.json.Writes

case class Shipment(date: String, country: String, weight: Long)

case class CarrierRates(id: Long, country: String, weight: Long, rate: Double)
object CarrierRates {
//   implicit val writes: Writes[CarrierRates] = (
//     (JsPath \ "id").write[Long] and
//       (JsPath \ "country").write[String] and
//       (JsPath \ "weight").write[Long] and
//       (JsPath \ "rate").write[Double]
//   )
  implicit val carrierWrites = Json.writes[CarrierRates]
}

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */
@Singleton
class HomeController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {

  /** Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method will be called when the application receives a `GET` request with a path of `/`.
    */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def upload = Action(parse.multipartFormData) { request =>
    request.body
      .file("csv")
      .map { csv =>
        val filename = Paths.get(csv.filename).getFileName
        val contentType = csv.contentType
        val path = Paths.get(s"/tmp/$filename")

        csv.ref.copyTo(path, replace = true)

        val rows = Source.fromFile(s"/tmp/$filename").getLines().filter(l => l.trim().length() > 0).drop(1)
        val cRows = Source.fromFile(play.Environment.simple.getFile(s"/app/rates.csv")).getLines().drop(1)

        val carrierRates = (cRows.map(row => row.split(",")) map {
          case Array(id, country, weight, rate) => CarrierRates(id.toLong, country, weight.toLong, rate.toDouble)
        }).toList.sortBy(_.rate)

        val shipments = (rows.map(row => row.split(",")) map {
          case Array(date, country, weight) => Shipment(date, country, weight.toLong)
        }).toList

        val rs: ListBuffer[CarrierRates] = ListBuffer();

        shipments.foreach(s => {
          val rates = carrierRates.filter(r => r.country.equals(s.country) && r.weight >= s.weight)
          rs.addOne(rates.distinctBy(_.id).headOption.getOrElse(CarrierRates(0, "", 0, 0)))
        })

        Ok(Json.toJson(rs))
      }
      .getOrElse {
        Redirect(routes.HomeController.index())
          .flashing("error" -> "Missing file")
      }
  }
}
