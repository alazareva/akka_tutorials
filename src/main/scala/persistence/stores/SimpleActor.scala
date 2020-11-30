package persistence.stores
import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}


  class SimpleActor extends PersistentActor with ActorLogging {

    var nMessages = 0

    override def persistenceId: String = "simple-persistent-actor"

    override def receiveCommand: Receive = {
      case "print" =>
        log.info(f"I have persisted $nMessages")
      case "snap" =>
        saveSnapshot(nMessages)
      case SaveSnapshotSuccess(_) => log.info("saved snapshot ok")
      case SaveSnapshotFailure(_, cause) => log.warning(f"failed saving snapshot $cause")
      case message =>
        persist(message) { e =>
          log.info(s"persisted $message")
          nMessages += 1
        }
    }

    override def receiveRecover: Receive = {
      case SnapshotOffer(_, payload: Int) =>
        log.info(f"recovered snapshot: $payload")
        nMessages = payload
      case RecoveryCompleted => log.info("Recovery done")
      case message =>
        log.info(s"recovered $message")
        nMessages += 1
    }
  }