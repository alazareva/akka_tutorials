package faulttolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

object StartingStoppingActors extends App {

  val system = ActorSystem("stoppingDemo")

  object Parent {
    case class StartChild(name: String)
    case class StopChild(name: String)
    case object Stop
  }

  class Parent extends Actor with ActorLogging {
    import Parent._
    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting child $name")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))
      case StopChild(name) =>
        log.info(s"Stopping $name")
        val childOption = children.get("name")
        childOption.foreach(ref => context.stop(ref))
      case Stop =>
        log.info("Stopping self")
        context.stop(self) // stops all children first
      case s: String => log.info(s)
    }
    override def receive: Receive = withChildren(Map.empty[String, ActorRef])
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  import Parent._

  val parent = system.actorOf(Props[Parent], "parent")

  parent ! StartChild("child1")
  val child = system.actorSelection("/user/parent/child1")
  child ! "hi child"
  parent ! StopChild("child1")

  parent ! StartChild("child2")
  val child2 = system.actorSelection("/user/parent/child2")
  child2 ! "hi child 2"
  parent ! Stop

  // (0 to 10 ).foreach(_ => parent ! "parent are you there")
  // (0 to 50 ).foreach(_ => child2 ! "child2 are you there")

  // Method 2 - special messages

  val looseActor = system.actorOf(Props[Child])

  looseActor ! "Hello"

  looseActor ! PoisonPill

  looseActor ! "Still there?"

  val terminatedActor = system.actorOf(Props[Child])

  terminatedActor !  "getting terminated"
  terminatedActor ! Kill // exception gets thrown
  terminatedActor !  "you're terminated"

  // Death watch

  class Watcher extends Actor with ActorLogging {
    import Parent._
    override def receive: Receive = {
      case StartChild(name) =>
        val child = context.actorOf(Props[Child], name)
        log.info(s"starting and watching $name")
        context.watch(child)
      case Terminated(ref) =>
        log.info(s"the ref $ref is stopped")

    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watchedChild")
  val watchedChild = system.actorSelection("/user/watcher/watchedChild")
  Thread.sleep(500)
  watchedChild ! PoisonPill

}
