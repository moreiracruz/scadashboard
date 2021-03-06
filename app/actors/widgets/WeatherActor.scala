package actors.widgets

import scala.util.{Failure, Success}

import play.api.Application
import play.api.libs.json.Json
import play.api.libs.ws.WS
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import actors.HubActor.{Error, Update}
import actors.WidgetFactory
import actors.helpers.TickActor
import actors.widgets.WeatherActor.WeatherConfig
import services.Services

object WeatherActor extends WidgetFactory {
  override type C = WeatherConfig
  override val configReader = Json.reads[C]
  override def props(hub: ActorRef, id: String, config: C, services: Services)(implicit app: Application) = Props(new WeatherActor(hub, id, config, services))
  protected case class WeatherConfig(city: String, country: Option[String], unit: Option[String], language: Option[String], interval: Option[Long])
}

class WeatherActor(hub: ActorRef, id: String, config: WeatherConfig, services: Services)(implicit app: Application) extends Actor with TickActor with ActorLogging {
  import context.dispatcher

  override val interval = config.interval.getOrElse(10l)
  val city = config.city
  val country = config.country.getOrElse("fr")
  val unit = config.unit.getOrElse("metric")
  val language = config.language.getOrElse("fr")

  val url = s"http://api.openweathermap.org/data/2.5/weather?q=$city,$country&units=$unit&lang=$language"

  val query = WS.url(url).withRequestTimeout(interval * 1000)

  override def receive = {
    case Tick =>
      query.get().onComplete {
        case Success(response) =>
          // Lol-code
          val httpCode = (response.json \ "cod").asOpt[Int].orElse((response.json \ "cod").asOpt[String].map(_.toInt)).getOrElse(500)
          httpCode match {
            case 200 => hub ! Update(id, response.json)
            case 404 => hub ! Error(s"Cannot find weather data for city $city:$country")
          }
        case Failure(ex) =>
          log.error(ex, "Cannot load weather")
          hub ! Error("Cannot load weather")
      }
  }
}
