package http.lowlevelServer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object LowLevelApi extends App {

  implicit val system = ActorSystem("low-level-api")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)

  val connectionSink = Sink.foreach[IncomingConnection](conn => {
    println(s"Accepted connection from ${conn.remoteAddress}")
  })
  /*

  val serverBindingFuture = serverSource.to(connectionSink).run()

  serverBindingFuture.onComplete {
    case Success(binding) =>
      println("Binding success")
    // binding.unbind() // maintains open connections
    // binding.terminate(2 seconds)
    case Failure(exception) => println(s"Binding failed $exception")
  }
   */

  // Method 1 synchronously

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello from Akka Http
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity =  HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Oops
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }

  // Method 1
  val httpSyncConnectionHandler =
    Sink.foreach[IncomingConnection](conn => conn.handleWithSyncHandler(requestHandler))

 //  serverSource.runWith(httpSyncConnectionHandler)

 //  Http().bindAndHandleSync(requestHandler, "localhost", 8000)

  // Method 2 async

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello from Akka Http
            |</body>
            |</html>
          """.stripMargin
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity =  HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Oops
            |</body>
            |</html>
          """.stripMargin
        ))
      )
  }



  // Note in production use different execution context

  val httpAsyncConnectionHandler =
    Sink.foreach[IncomingConnection](conn => conn.handleWithAsyncHandler(asyncRequestHandler))

  // serverSource.runWith(httpAsyncConnectionHandler) // OR

  // Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8080)

  // Method 3 async via akka stream

  val streamsRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map{
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello from Akka Http
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity =  HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Oops
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }
  /*
  Http().bind("localhost", 8000).runForeach {
    connection => connection.handleWith(streamsRequestHandler)
  } // OR

  Http().bindAndHandle(streamsRequestHandler, "localhost", 8000)
   */

  // Ex: create server on port 8388 which replies welcome, with html on /about, 404 on other

  val streamsRequestHandlerEx: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map{
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com")),
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Welcome
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity =  HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Oops
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }

  val bindingFuture = Http().bindAndHandle(streamsRequestHandlerEx, "localhost", 8388)

  // to shutdown
  bindingFuture.flatMap(binding => binding.unbind()).onComplete(_ => system.terminate())
}
