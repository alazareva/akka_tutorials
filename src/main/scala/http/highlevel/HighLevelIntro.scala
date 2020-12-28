package http.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object HighLevelIntro extends App {

  implicit val system = ActorSystem("high-level-https")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") {
      complete(StatusCodes.OK)
    }

  val pathGetRoute: Route = {
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }
  }

  val chainedRoute: Route = {
    path("myendpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      post {
        complete(StatusCodes.Forbidden)
      }
    } ~ path("home") {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          |<body>
          |Hi
          |</body>
          |</html>
        """.stripMargin
      ))
    }
  }
    Http().bindAndHandle(simpleRoute, "localhost", 8080)

}
