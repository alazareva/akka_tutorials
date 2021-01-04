package http.client

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import spray.json._
import http.client.PaymentSystemDomain.PaymentRequest

import scala.util.{Failure, Success}

object HostLevel extends App  with PaymentJsonProtocol with SprayJsonSupport {

  implicit val system = ActorSystem("host-level")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // high volume, low latency

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")

  /*
  Source(1 to 10).map(i => (HttpRequest(), i))
    .via(poolFlow).map{
    case (Success(r), v) =>
      r.discardEntityBytes()
      s"Resp $r for request $v"
    case (Failure(e), v) =>
      s"Failed $e for request $v"
  }.runWith(Sink.foreach(println))
   */

  val creditCards = List(
    CreditCard("111111", "222", "acc"),
    CreditCard("1234-1234-1234-1234", "222", "acc"),
    CreditCard("111111", "222", "acc"),
  )

  val paymentRequests = creditCards.map(cc => PaymentRequest(cc, "onlinestore", 33))

  val serverRequests = paymentRequests.map(pr =>
    (HttpRequest(
      HttpMethods.POST,
      Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        pr.toJson.prettyPrint
      )
    ),
      UUID.randomUUID().toString
    )
  )

  Source(serverRequests).via(Http()
    .cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach {
      case (Success(r), oid) => println(s"Oid $oid returned $r")
      case (Failure(e), oid) => println(s"Oid $oid failed $e")
    }
}
