package persistence.eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object Snapshots extends App {

  // after each persist maybe save snapshot
  // if you save snapshot, handle SnapshotOffer in recover
  // handle save snapshot success/failure in receive

  case class SentMessage(content: String)

  case class ReceiveMessage(content: String)

  case class SentMessageRecord(id: Int, content: String)

  case class ReceiveMessageRecord(id: Int, content: String)

  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {

    val maxMessages = 10
    var currentId = 0
    val lastMessages = new mutable.Queue[(String, String)]()
    var commandsWithNoCheckpoints = 0

    override def persistenceId: String = f"$owner-$contact"

    override def receiveCommand: Receive = {
      case "print" => log.info(f"messages $lastMessages")
      case ReceiveMessage(content) =>
        persist(ReceiveMessageRecord(currentId, content)) { e =>
          log.info(f"received $content")
          update(contact, content)
          currentId += 1
          maybeCheckpoint()
        }
      case SentMessage(content) =>
        persist(SentMessageRecord(currentId, content)) { e =>
          log.info(f"sent $content")
          update(owner, content)
          currentId += 1
          maybeCheckpoint()
        }
      case SaveSnapshotSuccess(metadata) => log.info(f"ok $metadata")
      case SaveSnapshotFailure(_, e) => log.warning(f"failed $e")
    }

    def update(from: String, content: String): Unit = {
      if (lastMessages.size >= maxMessages) lastMessages.dequeue()
      lastMessages.enqueue((from, content))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithNoCheckpoints += 1
      if (commandsWithNoCheckpoints >= maxMessages) {
        log.info("checkpointing")
        saveSnapshot(lastMessages) // is async
        commandsWithNoCheckpoints = 0
      }
    }

    override def receiveRecover: Receive = {
      case SnapshotOffer(metadata, contents) =>
        log.info(f"recovered snapshot $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
      case ReceiveMessageRecord(id, content) =>
        log.info(f"recovered received $id $content")
        update(contact, content)
        currentId = id
      case SentMessageRecord(id, content) =>
        log.info(f"recovered sent $id $content")
        update(owner, content)
        currentId = id
    }
  }

  val system = ActorSystem("snapshots")
  val chat = system.actorOf(Chat.props("me", "other"))

  (0 until 1000).foreach { i =>
   // chat ! ReceiveMessage(f"akka rocks $i")
   // chat ! SentMessage(f"akka rules $i")
  }
  chat ! "print"
}
