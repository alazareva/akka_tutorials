package http.highlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import spray.json._

case class Player(nickname: String, character: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPlayersByClass(character: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._
  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all players")
      sender() ! players.values.toList
    case GetPlayer(nick) =>
      log.info(f"getting player $nick")
      sender() ! players.get(nick)
    case GetPlayersByClass(character) =>
      log.info(f"getting players $character")
      sender() ! players.values.filter(_.character == character).toList
    case AddPlayer(player) =>
      log.info(f"adding player $player")
      players += player.nickname -> player
      sender() ! OperationSuccess
    case RemovePlayer(player) =>
      log.info(f"removing player $player")
      players -= player.nickname
      sender() ! OperationSuccess
  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

object MarshallingJson extends App with PlayerJsonProtocol with SprayJsonSupport {
  import GameAreaMap._


  implicit val system = ActorSystem("marshalling")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  val gameActor = system.actorOf(Props[GameAreaMap], "game")

  val players = List(
    Player("foo", "dog", 20),
    Player("bar", "cat", 1),
    Player("bob", "dog", 1),
  )

  players.foreach(p => gameActor ! AddPlayer(p))

  /**
    * GET /api/player
    * GET /api/player/nickname
    * GET /api/player?nickname=x
    * GET /api/player/class/x
    * POST /api/player with json
    * DELETE /api/player with json
    */

  implicit val timeout: Timeout = Timeout(2 seconds)

  val playerRoutes =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { char =>
          val playersByClass = (gameActor ? GetPlayersByClass(char)).mapTo[List[Player]]
          complete(playersByClass)
        } ~ {
          (path(Segment) | parameter('nickname)) { nick =>
            val playerByNick = (gameActor ? GetPlayer(nick)).mapTo[Option[Player]]
            complete(playerByNick)
          }
        } ~ pathEndOrSingleSlash {
          val allPlayers = (gameActor ? GetAllPlayers).mapTo[List[Player]]
          complete(allPlayers)
        }
      } ~ post {
        entity(as[Player]) { player =>
          val added = (gameActor ? AddPlayer(player)).map(_ => StatusCodes.OK)
          complete(added)
        }
      } ~ delete {
        entity(as[Player]) { player =>
          val added = (gameActor ? RemovePlayer(player)).map(_ => StatusCodes.OK)
          complete(added)
        }
      }
    }

  Http().bindAndHandle(playerRoutes, "localhost", 8080)


}
