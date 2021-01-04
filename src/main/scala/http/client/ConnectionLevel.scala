package http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import PaymentSystemDomain._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ConnectionLevel extends App with PaymentJsonProtocol with SprayJsonSupport {

  implicit val system = ActorSystem("conn-client")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // long lived outgoing connection
  val connectionFlow = Http().outgoingConnection("www.google.com")

  def oneOffRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

  oneOffRequest(HttpRequest()).onComplete {
    case Success(value) => println(f"got $value")
    case Failure(exception) => println(f"failed $exception")
  }

  /*
  A small payment system
   */

  val creditCards = List(
    CreditCard("111111", "222", "acc"),
    CreditCard("1234-1234-1234-1234", "222", "acc"),
    CreditCard("111111", "222", "acc"),
  )

  val paymentRequests = creditCards.map(cc => PaymentRequest(cc, "onlinestore", 33))

  val serverRequests = paymentRequests.map(pr =>
    HttpRequest(
      HttpMethods.POST,
      Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        pr.toJson.prettyPrint
      )
    )
  )
  Source(serverRequests).via(Http().outgoingConnection("localhost", 8080)).to(Sink.foreach(println)).run()
}
