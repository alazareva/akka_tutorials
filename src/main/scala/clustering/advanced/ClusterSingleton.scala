package clustering.advanced

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

/*
  A small payment system - centralized
  benefits - automatic handover
  cons - single point of failure, not 100% uptime
 */

case class Order(items: List[String], total: Double)

case class Transaction(orderId: Int, transactionId: String, amount: Double)

class PaymentSystem extends Actor with ActorLogging {

  override def receive: Receive = {
    case t: Transaction => log.info(s"Validating transaction $t")
    case m => log.info(s"Unknown message $m")
  }
}

class PaymentSystemNode(port: Int) extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = $port
    """.stripMargin
  ).withFallback(ConfigFactory.load("clustering/clusterSingleton.conf"))

  val system = ActorSystem("cluster", config)

  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props[PaymentSystem],
      terminationMessage = PoisonPill,
      ClusterSingletonManagerSettings(system)
    ),
    "paymentSystem"
  )
}

object Node1 extends PaymentSystemNode(2551)

object Node2 extends PaymentSystemNode(2552)

object Node3 extends PaymentSystemNode(2553)

class OnlineShopCheckout(paymentSystem: ActorRef) extends Actor with ActorLogging {

  var orderId = 0

  override def receive: Receive = {
    case Order(_, amount) =>
      log.info(s"Got order $orderId sending transaction")
      val transaction = Transaction(orderId, UUID.randomUUID().toString, amount)
      paymentSystem ! transaction
      orderId += 1
  }
}

object OnlineShopCheckout {

  def props(paymentSystem: ActorRef) = Props(new OnlineShopCheckout(paymentSystem))
}

object PaymentSystemClient extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 0
    """.stripMargin
  ).withFallback(ConfigFactory.load("clustering/clusterSingleton.conf"))

  val system = ActorSystem("cluster", config)

  val proxy = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/paymentSystem",
      settings = ClusterSingletonProxySettings(system),
    ),
    "paymentSystemProxy"
  )

  val onlineShopCheckout = system.actorOf(OnlineShopCheckout.props(proxy))

  import system.dispatcher

  system.scheduler.schedule(5 seconds, 1 second, () => {
    val randomOrder = Order(List(), Random.nextDouble * 100)
    onlineShopCheckout ! randomOrder
  })
}