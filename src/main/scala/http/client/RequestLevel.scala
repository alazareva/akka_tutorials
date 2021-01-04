package http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import http.client.PaymentSystemDomain.PaymentRequest
import spray.json._

import scala.util.{Failure, Success}

object RequestLevel extends App with PaymentJsonProtocol with SprayJsonSupport {

  // Do not use for high volume requests

  implicit val system = ActorSystem("request-level")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri="http://www.google.com"))
  responseFuture.onComplete {
    case Success(value) =>
      value.discardEntityBytes()
      println(s"success $value")
    case Failure(exception) =>  println(s"failed $exception")
  }

  val creditCards = List(
    CreditCard("111111", "222", "acc"),
    CreditCard("1234-1234-1234-1234", "222", "acc"),
    CreditCard("111111", "222", "acc"),
  )

  val paymentRequests = creditCards.map(cc => PaymentRequest(cc, "onlinestore", 33))

  val serverRequests = paymentRequests.map(pr =>
    HttpRequest(
      HttpMethods.POST,
      Uri("http://localhost:8080/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        pr.toJson.prettyPrint
      )
    )
  )

  Source(serverRequests)
    .mapAsync(10)(req => Http().singleRequest(req))
    .runForeach(println)
}
