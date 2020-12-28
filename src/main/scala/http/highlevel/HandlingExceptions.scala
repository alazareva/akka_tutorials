package http.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler

object HandlingExceptions extends App {

  implicit val system = ActorSystem("exceptions")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  import akka.http.scaladsl.server.Directives._

  val simpleRoute =
    path("api" / "people") {
      get {
        throw new RuntimeException("too long")
      } ~ post {
        parameter('id) { id =>
          if (id.length > 2) throw new NoSuchElementException(f"$id not found")
          complete(StatusCodes.OK)
        }
      }
    }

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException => complete(StatusCodes.BadRequest, e.getMessage)
  }

  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException => complete(StatusCodes.NotFound, e.getMessage)
  }

  val noSuchElementHandler: ExceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException => complete(StatusCodes.BadRequest, e.getMessage)
  }

  val delicateHandleRoute = handleExceptions(runtimeExceptionHandler) {
    path("api" / "people") {
      get {
        throw new RuntimeException("too long")
      } ~ handleExceptions(noSuchElementHandler) { post
        parameter('id) { id =>
          if (id.length > 2) throw new NoSuchElementException(f"$id not found")
          complete(StatusCodes.OK)
        }
      }
    }
  }

  Http().bindAndHandle(delicateHandleRoute, "localhost", 8080)
}
