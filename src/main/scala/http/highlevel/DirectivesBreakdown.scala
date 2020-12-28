package http.highlevel

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {

  implicit val system = ActorSystem("directives")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import akka.http.scaladsl.server.Directives._

  // filtering

  val simpleHttpMethodRoute = post { // get, put, patch, delete, head, options
    complete(StatusCodes.Forbidden)
  }

  val simplePathRoute =
    path("about") {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html></html>
        """.stripMargin
      ))
    }

  val complexPathRoute =
    path("api" / "endpoint") { // api/endpoint
      complete(StatusCodes.OK)
    }

  val dontConfuse =  path("api/endpoint") { // will be url-encoded
    complete(StatusCodes.OK)
  }

  val pathEndRoute = pathEndOrSingleSlash { // matches localhost:8080 or localhost:8080/
    complete(StatusCodes.OK)
  }

  val multiExtract = path("api" / "order" / IntNumber / IntNumber) { (id, inventory) =>
    print(s"Id $id, inv $inventory")
    complete(StatusCodes.OK)
  }

  // Type 2: Extraction

  // get on api/item/42

  val pathExtractionRoute =
    path("api" / "item" / IntNumber) { item: Int =>
      println(s"Item $item")
      complete(StatusCodes.OK)
    }


  // query params

  // api/item?id=45
  val queryParamExtract =  path("api" / "item") {
    parameter('id.as[Int]) { item: Int => // 'id is a symbol, performance gain
      println(s"got id $item")
      complete(StatusCodes.OK)
    }
  }

  val extractRequestRoute =
    path("endpoint") {
      extractRequest { httpRequest: HttpRequest =>
        extractLog { log: LoggingAdapter =>
          log.info(f"got $httpRequest")
          complete(StatusCodes.OK)

        }
      }
    }

  // Type 3 composite directives

  val simpleNestedRoute =
    path("api"  / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val compactNestedRoute = (path("api"  / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("endpoint") & extractRequest & extractLog) { (request, log) =>
      log.info(s"got $request")
      complete(StatusCodes.OK)
    }


  val dryRoute = (path("about") | path("aboutUS")) {
    complete(StatusCodes.OK)
  }

  // blog.com/42  blog.com?postId=42

  val blogById =
    path(IntNumber) { id =>
      println(id)
      complete(StatusCodes.OK)
    }

  val blogByParam = {
    parameter('postId.as[Int]) { id =>
      println(id)
      complete(StatusCodes.OK)
    }
  }

  val combinedBlogById = (path(IntNumber) | parameter('postId.as[Int])) { id =>
    println(id)
    complete(StatusCodes.OK)
  }

  // Type 4 Actionable directives

  val completeRoute = complete(StatusCodes.OK)

  val failedRoute = path("unsupported") {
    failWith(new RuntimeException("Unsupported")) // completes with 500
  }

  // rejections

  val routeWithRejection = {
    path("home") {
      reject
    } ~ path("index") {
      completeRoute
    }
  }

  val getOrPut =
    path("api" / "endpoint") {
      get {
        completeRoute
      } ~
      post {
        complete(StatusCodes.Forbidden)
      }
    }


  Http().bindAndHandle(queryParamExtract, "localhost", 8080)


}
