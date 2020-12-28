package http.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import http.lowlevelServer.Guitar
import http.lowlevelServer.GuitarDb.{CreateGuitar, GuitarCreated}
import http.lowlevelServer.LowLevelRest.guitarDb
import scala.concurrent.duration._
import spray.json._

case class Person(pin: Int, name: String)

trait PersonProtocol extends DefaultJsonProtocol {

  implicit val guitarFormat = jsonFormat2(Person)
}

object HighLevelExercise extends App with PersonProtocol {
  implicit val system = ActorSystem("HighLevelExercise")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import akka.http.scaladsl.server.Directives._


  /**
    * GET /api/people all the ppl
    * GET /api/people/pin get person with pin
    * GET /api/people?pin=xx
    * POST /api/people with person
    * extracting entity
   */

  var people = List(Person(1, "bob"), Person(2, "bob")).map(p => p.pin -> p).toMap

  def toHttpEntity(payload: String): HttpEntity.Strict = {
    HttpEntity(
      ContentTypes.`application/json`,
      payload
    )
  }

  val routes =
    pathPrefix("api" / "people") {
      ((parameter('pin.as[Int]) | path(IntNumber)) & get) { pid =>
        complete(toHttpEntity(people.get(pid).toJson.prettyPrint))
      } ~ pathEndOrSingleSlash {
        get {
          complete(toHttpEntity(people.values.toJson.prettyPrint))
        } ~ post {
          entity(as[HttpEntity]) { e =>
            val strictEntityFuture = e.toStrict(3 seconds)
            val resF = strictEntityFuture.map { strictEntity =>
              val person = strictEntity.data.utf8String.parseJson.convertTo[Person]
              people += (person.pin -> person)
              toHttpEntity(people.values.toJson.prettyPrint)
            }
            complete(resF)
          }
        }
      }
    }

  Http().bindAndHandle(routes, "localhost", 8080)

}
