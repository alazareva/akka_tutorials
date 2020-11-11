package akkaessentials.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, FromConfig, RoundRobinGroup, RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

object Routers extends App {


  class Parent extends Actor {

    // create routees
    private val children = (0 until 5).map { i =>
      val child = context.actorOf(Props[Child], f"$i")
      context.watch(child)
      ActorRefRoutee(child)
    }
    // create router
    private val router = Router(RoundRobinRoutingLogic(), children)

    override def receive: Receive = {
      // route message
      case message => router.route(message, sender())
      case Terminated(ref) => // from the death watch
        router.removeRoutee(ref)
        val newChild = context.actorOf(Props[Child])
        context.watch(newChild)
        router.addRoutee(newChild)
    }
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("routing", ConfigFactory.load().getConfig("routersDemo"))
  val parent = system.actorOf(Props[Parent])

  // (0 to 10).foreach(i => parent ! f"hi $i")

  // method 2, a router actor Pool router

  val poolParent = system.actorOf(RoundRobinPool(5).props(Props[Child]), "simpleParent")

  // (0 to 10).foreach(i => poolParent ! f"hi $i")

  // Method 3 using application

  val poolParent2 = system.actorOf(FromConfig.props(Props[Child]), "poolParent2")
  //(0 to 10).foreach(i => poolParent2 ! f"hi $i")

  // 4 actors with actors created somewhere else

  val children = (0 until 5).map(i => system.actorOf(Props[Child], f"child$i"))
  val childPaths = children.map(_.path.toString)
  val groupRouter = system.actorOf(RoundRobinGroup(childPaths).props())
  //  (0 to 10).foreach(i => groupRouter ! f"hi $i")

  // from configuration

  val groupParent2 = system.actorOf(FromConfig.props(), "groupParent2")
  //(0 to 10).foreach(i => groupParent2 ! f"hi $i")

  // special messages

  groupParent2 ! Broadcast("hi all")

  // PoisonPill and Kill not routed
  // AddRoutee, Remove, Get handled only by router
}
