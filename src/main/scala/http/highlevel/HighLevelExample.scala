package http.highlevel

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import http.lowlevelServer.GuitarDb.CreateGuitar
import http.lowlevelServer.{Guitar, GuitarDb, GuitarStoreJsonProtocol}
import http.lowlevelServer.GuitarDb._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.duration._


object HighLevelExample extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("high-level-example")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  // GET /api/guitar
  // GET /api/guitar?id=x
  // GET /api/guitar/x
  // GET /api/inventory?isActive=true

  val guitarDb = system.actorOf(Props[GuitarDb], "guitarbd")

  val guitarList = List(
    Guitar("fender", "strata"),
    Guitar("gibson", "les paul"),
    Guitar("martin", "lx1"),
  )

  guitarList.foreach { guitar => guitarDb ! CreateGuitar(guitar) }

  implicit val timeout: Timeout = Timeout(2 seconds)
  val guitarServerRoute =
    path("api" / "guitar" / IntNumber) {
      { gId =>
        get {
          val guitarsFuture = (guitarDb ? FindGuitar(gId)).mapTo[Option[Guitar]]
          val gEntity = guitarsFuture.map(go => {
            HttpEntity(
              ContentTypes.`application/json`,
              go.toJson.prettyPrint
            )
          })
          complete(gEntity)
        }
      }
    } ~ path("api" / "guitar") {
      get {
        val guitarsFuture = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        val gEntity = guitarsFuture.map(lg => {
          HttpEntity(
            ContentTypes.`application/json`,
            lg.toJson.prettyPrint
          )
        })
        complete(gEntity)
      } ~ parameter('id.as[Int]) { gId =>
        get {
          val guitarsFuture = (guitarDb ? FindGuitar(gId)).mapTo[Option[Guitar]]
          val gEntity = guitarsFuture.map(go => {
            HttpEntity(
              ContentTypes.`application/json`,
              go.toJson.prettyPrint
            )
          })
          complete(gEntity)
        }
      }
    } ~ path("api" / "guitar" / "inventory") {
      get {
        parameter('inStock.as[Boolean]) { inStock =>
          val guitarsFuture = (guitarDb ? GetInventory(inStock)).mapTo[List[Guitar]]
          val gEntity = guitarsFuture.map(lg => {
            HttpEntity(
              ContentTypes.`application/json`,
              lg.toJson.prettyPrint
            )
          })
          complete(gEntity)
        }
      }
    }

  def toHttpEntity(payload: String): HttpEntity.Strict = {
    HttpEntity(
      ContentTypes.`application/json`,
      payload
    )
  }

  val simplified =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          val guitarsFuture = (guitarDb ? GetInventory(inStock))
            .mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
          complete(guitarsFuture)
        }
      } ~ (parameter('id.as[Int]) | path(IntNumber)) { gId =>
        val guitarsFuture = (guitarDb ? FindGuitar(gId)).mapTo[Option[Guitar]].map(_.toJson.prettyPrint).map(toHttpEntity)
        complete(guitarsFuture)
      } ~ pathEndOrSingleSlash {
        val guitarsFuture = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]].map(_.toJson.prettyPrint).map(toHttpEntity)
        complete(guitarsFuture)
      }
    }

  Http().bindAndHandle(simplified, "localhost", 8080)
}
