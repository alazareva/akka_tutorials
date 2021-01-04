package http.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.CompactByteString
import akka.http.scaladsl.server.Directives._
import scala.concurrent.duration._

object Websockets extends App {

  implicit val system = ActorSystem("websockets")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // message type TextMessage vs BinaryMessage

  val textMessage = TextMessage(Source.single("Hello via text"))
  val binaryMessage = BinaryMessage(Source.single(CompactByteString("Hello via binary")))

  val html = """<html>
               |    <head>
               |        <script>
               |            var exampleSocket = new WebSocket("ws://localhost:8080/greeter");
               |            console.log("starting socket");
               |            exampleSocket.onmessage = function(event) {
               |                var child = document.createElement("div")
               |                child.innerText = event.data;
               |                document.getElementById("1").appendChild(child)
               |            };
               |
               |            exampleSocket.onopen = function(event) {
               |
               |                exampleSocket.send("socket is open")
               |            };
               |
               |            exampleSocket.send("socket says hello server");
               |
               |        </script>
               |    </head>
               |<body>
               |    Starting websocket <div id="1"></div>
               |</body>
               |</html>""".stripMargin

  def websocketFlow: Flow[Message, Message, Any] = Flow[Message].map {
    case tm: TextMessage =>
      TextMessage(Source.single("Server says back: ") ++ tm.textStream ++ Source.single("!"))
    case bm: BinaryMessage =>
      bm.dataStream.runWith(Sink.ignore)
      TextMessage(Source.single("Server got binary message"))
  }
  val websocketRoute = (pathEndOrSingleSlash & get) {
    complete(HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      html
    ))
  } ~ path("greeter") {
    handleWebSocketMessages(socialFlow)
  }

  case class SocialPost(owner: String, content: String)

  val socialFeed = Source(List(
    SocialPost("Martin", "Scala 3 is announced"),
    SocialPost("RTJVM", "new class open"),
    SocialPost("Martin", "I killed java"),
  ))

  val socialMessages = socialFeed.throttle(1, 2 seconds).map(sp => TextMessage(s"${sp.owner} said ${sp.content}"))

  val socialFlow: Flow[Message, Message, Any] = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),
    socialMessages
  )

  Http().bindAndHandle(websocketRoute, "localhost", 8080)
}
