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
import java.util.Arrays

case class Shipment(date: String, country: String, weight: Long)

case class CarrierRates(id: Long, country: String, weight: Long, rate: Long)
object CarrierRates {
  implicit val carrierWrites = Json.writes[CarrierRates]
  implicit val empty = CarrierRates(0L, "", 0L, Long.MaxValue)
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
        val path = Paths.get(s"/tmp/$filename")

        csv.ref.copyTo(path, replace = true)

        val rows = Source.fromFile(s"/tmp/$filename").getLines().filter(l => l.trim().length() > 0).drop(1)
        val cRows = Source.fromFile(play.Environment.simple.getFile(s"/app/rates.csv")).getLines().drop(1)

        val carrierRates = (cRows.map(row => row.split(",")) map {
          case Array(id, country, weight, rate) => CarrierRates(id.toLong, country, weight.toLong, rate.toLong)
        }).toList.sortBy(_.weight).groupBy(_.country)

        val shipments = (rows.map(row => row.split(",")) map {
          case Array(date, country, weight) => Shipment(date, country, weight.toLong)
        }).toList

        val rs = ListBuffer[CarrierRates]()

        shipments.foreach { s =>
          val rates = carrierRates.get(s.country) match {
            case Some(rate) =>
              rate
                .groupBy(_.id)
                .foldLeft(CarrierRates.empty)({
                  case (cr1, cr2) => {
                    val r = rateForWeight(s.weight, cr2._2.collect(_.weight), cr2._2.collect(_.rate))
                    if (cr1.rate > r) CarrierRates(cr2._1, s.country, s.weight, r) else cr1
                  }
                })
            case None => throw new IllegalArgumentException()
          }
          rs.addOne(rates)
        }
        Ok(Json.toJson(rs))
      }
      .getOrElse {
        Redirect(routes.HomeController.index())
          .flashing("error" -> "Missing file")
      }
  }

  val calculateShipping = (weight: Long, weights: List[Long]) => weights.indexWhere(w => weight >= w)

  def rateForWeight(weight: Long, weights: List[Long], rates: List[Long]): Long = {
    val i = calculateShipping(weight, weights)
    if (weight <= 0 || i == -1)
      return 0L
    return rates(i) + rateForWeight(weight - weights(i), weights, rates)
  }
}
