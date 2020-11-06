package infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, PoisonPill, Props, Timers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("timersDemo")
  val simpleActor = system.actorOf(Props[SimpleActor])

  implicit val ec: ExecutionContext = system.dispatcher
  // or import system.dispatcher

  system.log.info("Scheduling reminder for simple Actor")

  system.scheduler.scheduleOnce(1 second){
    simpleActor ! "reminder"
  }

  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) {
    simpleActor ! "heartbeat"
  }

  system.scheduler.scheduleOnce(5 seconds) {
    routine.cancel()
  }

  // Ex implement a self-closing actor
  // if actor gets a message, you have 1 second to send it another message
  // if the time window expires the actor will stop itself
  // if you send another message the time is reset

  class SelfClosingActor extends Actor with ActorLogging {
    var schedule = createTimeoutWindow()

    def createTimeoutWindow(): Cancellable = {
      context.system.scheduler.scheduleOnce(1 second) {
        self ! PoisonPill
      }
    }
    override def postStop(): Unit = {
      log.warning("I shut down")
    }
    override def receive: Receive = {
      case msg =>
        log.info(msg.toString)
        schedule.cancel()
        schedule = createTimeoutWindow()
    }
  }

  val shutdownActor = system.actorOf(Props[SelfClosingActor])
  shutdownActor ! "hi"
  shutdownActor ! "hi"

  // timer

  case object TimerKey
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedHeartbeat extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(TimerKey, Start, 1 second) // sends Start after 1 second
    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second) // sends Reminder every second
      case Reminder =>
        log.info("alive")
      case Stop =>
        log.warning("Stopping")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val heartbeatActor = system.actorOf(Props[TimerBasedHeartbeat])

  system.scheduler.scheduleOnce(5 second) {
    heartbeatActor ! Stop
  }

}
