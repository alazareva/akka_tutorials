package akkaessentials.advancedactors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {

  // resource actor, if open can get read/write requests otherwise postpone before open

  // Closed(open) -> Open (read/write/close), RW messages are postponed

  // [Open, Read, Read Write] ok
  // [Read, Open, Write] Read is postponed in stash
  // when open, prepend stash to mailbox

  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // mixin Stash trait, stash messages then unstashAll when yuo switch context

  class ResourceActor extends Actor with ActorLogging with Stash {
    private var innerData: String = ""

    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("opening")
        unstashAll()
        context.become(open)
      case message =>
        log.info(f"stashing $message")
        stash()
    }

    def open: Receive = {
      case Read =>
        log.info(f"read $innerData")
      case Write(d) =>
        log.info(f"writing $d")
        innerData = d
      case Close =>
        log.info("closing")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(f"stashing $message")
        stash()
    }
  }

  val system = ActorSystem("stashDemo")

  val resourceActor = system.actorOf(Props[ResourceActor])

  resourceActor ! Read // stashed
  resourceActor ! Open // pop Read off stack
  resourceActor ! Open // stashed
  resourceActor ! Write("I have stash") // handled
  resourceActor ! Close // handled and Open popped
  resourceActor ! Read // handled

}
