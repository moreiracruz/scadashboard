package actors.widgets

import scala.util._

import play.api.Application
import play.api.libs.json.Json
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import actors.HubActor.{Error, Update}
import actors.WidgetFactory
import actors.helpers.TickActor
import actors.widgets.TwitterUserActor.TwitterUserConfig
import services.Twitter

object TwitterUserActor  extends WidgetFactory {
  override type C = TwitterUserConfig
  override val configReader = Json.reads[C]
  override def props(hub: ActorRef, id: String, config: C)(implicit app: Application) = Props(new TwitterUserActor(hub, id, config))
  protected case class TwitterUserConfig(username: String, interval: Option[Long])
}

class TwitterUserActor(hub: ActorRef, id: String, config: TwitterUserConfig)(implicit app: Application) extends Actor with TickActor with ActorLogging {
  import context.dispatcher

  override val interval = config.interval.getOrElse(60l)

  val twitter = app.injector.instanceOf(classOf[Twitter])

  override def receive = {
    case Tick =>
      twitter
        .loadUserInformation(config.username)
        .map {
          case Left(error) => hub ! Error(error)
          case Right(data) => hub ! Update(id, data)
        }
  }

}
