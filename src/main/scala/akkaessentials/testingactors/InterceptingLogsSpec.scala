package akkaessentials.testingactors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

object InterceptingLogsSpec {

  case class Checkout(item: String, creditCard: String)

  case class AuthorizeCard(creditCard: String)

  case object PaymentAccepted

  case object PaymentDenied

  case class DispatchOrder(item: String)

  case object OrderConfirmed

  class CheckoutActor extends Actor {
    private val paymentManager = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager = context.actorOf(Props[FulfillmentManager])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout: Receive = {
      case Checkout(item, card) => paymentManager ! AuthorizeCard(card)
        context.become(pendingPayment(item))

    }

    def pendingPayment(item: String): Receive = {
      case PaymentAccepted =>
        fulfillmentManager ! DispatchOrder(item)
        context.become(pendingFulfilment(item))
      case PaymentDenied => throw new RuntimeException("I can't do this")
    }

    def pendingFulfilment(item: String): Receive = {
      case OrderConfirmed => context.become(awaitingCheckout)
    }

  }

  class PaymentManager extends Actor {
    override def receive: Receive = {
      case AuthorizeCard(card) => if (card.startsWith("0")) sender() ! PaymentDenied else sender() ! PaymentAccepted
    }

  }

  class FulfillmentManager extends Actor with ActorLogging {
    var orderId = 0

    override def receive: Receive = {
      case DispatchOrder(item) =>
        orderId += 1
        log.info(s"Order $orderId for item $item is dispatched")
        sender() ! OrderConfirmed
    }

  }

}

class InterceptingLogsSpec extends
  TestKit(ActorSystem("interceptingLogsSpec", ConfigFactory.load().getConfig("interceptingLogMessages"))) with
  ImplicitSender with
  WordSpecLike with
  BeforeAndAfterAll {

  import InterceptingLogsSpec._

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "a checkout flow" should {
    val item = "dog"
    val cc = "12234"
    "correctly log dispatch order" in {
      EventFilter.info(pattern = s"Order [0-9]+ for item $item is dispatched", occurrences = 1) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, cc)
      }
    }

    "freak out if payment denied" in {
      EventFilter[RuntimeException](occurrences = 1) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, "0000")
      }
    }
  }

}
