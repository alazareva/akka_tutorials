package http.client

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._


case class CreditCard(number: String, securityCode: String, account: String)

object PaymentSystemDomain {

  case class PaymentRequest(creditCard: CreditCard, receiverAccount: String, amount: Double)
  case object PaymentAccepted
  case object PaymentRejected
}

trait PaymentJsonProtocol extends DefaultJsonProtocol {

  implicit val ccFormat = jsonFormat3(CreditCard)
  implicit val paymentFormat = jsonFormat3(PaymentSystemDomain.PaymentRequest)
}

class PaymentValidator extends Actor with ActorLogging {
  import PaymentSystemDomain._
  override def receive: Receive = {
    case PaymentRequest(CreditCard(number, _, _), receiverAccount, amount) =>
      log.info(f"sending $amount from $number")
      if (number == "1234-1234-1234-1234") sender() ! PaymentRejected
      else sender() ! PaymentAccepted
  }
}

object PaymentSystem extends App with PaymentJsonProtocol with SprayJsonSupport {
  import PaymentSystemDomain._

  implicit val system = ActorSystem("payment-system")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  implicit val timeout: Timeout = Timeout(2 seconds)

  val paymentValidator = system.actorOf(Props[PaymentValidator])

  val paymentRoute = path("api" / "payments") {
    post {
      entity(as[PaymentRequest]) { payment =>
        val validationResponse = (paymentValidator ? payment).map {
          case PaymentRejected => StatusCodes.Forbidden
          case PaymentAccepted => StatusCodes.OK
          case _ => StatusCodes.BadRequest
        }
        complete(validationResponse)
      }
    }
  }

  Http().bindAndHandle(paymentRoute, "localhost", 8080)
}
