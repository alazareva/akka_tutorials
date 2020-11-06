package faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

object ActorLifecycle extends App {

  object LifecycleActor {

    case object StartChild

  }

  class LifecycleActor extends Actor with ActorLogging {

    import LifecycleActor._

    override def receive: Receive = {
      case StartChild =>
        context.actorOf(Props[LifecycleActor], "child")
    }

    override def preStart(): Unit = log.info("I am starting")

    override def postStop(): Unit = log.info("I am stopping")
  }


  import LifecycleActor._

  val system = ActorSystem("LifecycleDemo")

  val parent = system.actorOf(Props[LifecycleActor], "parent")

  parent ! StartChild
  parent ! PoisonPill

  // restart

  object Fail

  object FailChild

  object Check

  object CheckChild

  class Child extends Actor with ActorLogging {
    override def preStart(): Unit = log.info("supervised child starting")

    override def postStop(): Unit = log.info("supervised child stopping")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.info(s"supervised restarting cuz of ${reason.getMessage}")
    }

    override def postRestart(reason: Throwable): Unit = log.info("supervised restarted")

    override def receive: Receive = {
      case Fail =>
        log.warning("child will fail")
        throw new RuntimeException("I failed")
      case Check => log.info("alive")
    }
  }

  class Parent extends Actor with ActorLogging {
    private val child = context.actorOf(Props[Child], "supervised")

    override def receive: Receive = {
      case FailChild => child ! Fail
      case CheckChild => child ! Check
    }
  }


  val supervisor = system.actorOf(Props[Parent], "sup")
  supervisor ! FailChild
  supervisor ! CheckChild

  // supervision strategy, if actor throws an exception the message that caused it is removed and it's restarted
}
