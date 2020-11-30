package persistence.eventsourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  case class Command(contents: String)
  case class Event(contents: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef) = Props(new CriticalStreamProcessor(eventAggregator))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {

    override def persistenceId: String = "critical-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! f"processing $contents"
        persistAsync(Event(contents)) /* Large Time gap */{ e => // commands don't get stashed but receive
          eventAggregator ! e
        }
        // come computation
      val processed = contents + "_pr"
        persistAsync(Event(processed))  /* Large Time gap */ { e =>
          eventAggregator ! e
        }
    }

    override def receiveRecover: Receive = {
      case message => log.info(f"recovered $message")
    }
  }

  class EventAggregator extends Actor with ActorLogging {

    override def receive: Receive = {
      case message => log.info(f"$message")
    }
  }

  val system = ActorSystem("async-persist")

  val eventAggregator = system.actorOf(Props[EventAggregator])

  val processor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "stream-processor")

  processor ! Command("c1")

  processor ! Command("c2")

  // when is it good to use persistAsync?
  /*
  Persist async is better performance, because there's not waiting for persistence
  good in high-throughput
  BUT it does not guarantee ordering of events
   */

}
