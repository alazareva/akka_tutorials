package http.highlevel

import akka.actor.ActorSystem
import akka.http.javadsl.server.MethodRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MissingQueryParamRejection, Rejection, RejectionHandler}

object HandlingRejections extends App {
  implicit val system = ActorSystem("rejections")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  import akka.http.scaladsl.server.Directives._

  val simpleRoute =
    path("api" / "endpoint") {
      get {
        complete(StatusCodes.OK)
      } ~ put {
        parameter('id) { _ =>
          complete(StatusCodes.OK)
        }
      }
    }

  // rejection is not a failure, it passes the request to the rest of routing tree
  val badRequestHandle: RejectionHandler = { rejections: Seq[Rejection] =>
    println(f"Got rejections $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(f"Got rejections $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandle) {
      path("api" / "endpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post {
            handleRejections(forbiddenHandler) {
              parameter('param) { _ =>
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }

  // RejectionHandler.default - is the default used if none provided

  implicit val customRejectionHandler = RejectionHandler.newBuilder()
      .handle {
        case  m: MethodRejection =>
          println(f"MethodRejection $m")
          complete("Rejected Method")
      }
      .handle {
        case m: MissingQueryParamRejection =>
          println(f"MissingQueryParamRejection $m")
          complete("MissingQueryParamRejection")
      }
      .result()



  Http().bindAndHandle(simpleRoute, "localhost", 8080)

}
