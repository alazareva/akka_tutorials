package persistence.eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {



    override def persistenceId: String = "fake-id"


    override def receiveCommand: Receive = online(0)

    def online(latestEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestEventId, contents)) { e =>
          log.info(f"saved $e recovery is finished: ${this.recoveryFinished}")
          context.become(online(latestEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info("I'm recovered")
      case Event(id, contents) =>
        // if (contents.contains("12")) throw  new RuntimeException("bad value")
        log.info(f"recovered $contents recovery is finished: ${this.recoveryFinished}")
        context.become(online(id + 1)) // this will NOT change the event handler during recovery!!!!!
        // AFTER recovery the normal handler will be result of the stacking of ALL context becomes
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    override def recovery: Recovery = Recovery(
      fromSnapshot = SnapshotSelectionCriteria.latest // from latest snapshot
    ) // or you can do Recovery.none
  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  (1 to 100).foreach(i => recoveryActor ! Command(f"$i"))

  // ALL COMMANDS IN RECOVERY ARE STASHED

  // failure during recovery
  // on recovery failure the actor is stopped

  // customizing recovery

  // no not persist more events after incomplete customized recovery

  // recovery status or knowing when you're done recovery
  // useful to get a message when recovery finished

  // Stateless actors

}
