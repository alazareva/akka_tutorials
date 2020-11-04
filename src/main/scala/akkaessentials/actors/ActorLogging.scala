package akkaessentials.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorLoggingApp extends App {


  class SimpleActorWithLogger extends Actor {
    val logger = Logging(context.system, this)
    override def receive: Receive = {
      case message => logger.info(message.toString)
    }
  }

  val system = ActorSystem("LoggingDemo")

  val actor = system.actorOf(Props[SimpleActorWithLogger])

  actor ! "log me"

  class ActorWithLogging extends Actor with ActorLogging {

    override def receive: Receive = {
      case (a, b) => log.info(s"two things $a and $b")
      case message => log.info(message.toString)
    }

  }

  val actor2 = system.actorOf(Props[ActorWithLogging])
  actor2 ! "and this"
  actor2 ! (1, 2)

}
