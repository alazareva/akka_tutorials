package persistence.eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActorsExercise extends App {

  case class Vote(citizenPID: String, candidate: String)
  case object Poll

  // actor keeps voted set and poll, actor must be able to recover its state

  class VotingActor extends PersistentActor with ActorLogging {

    val votedSet = collection.mutable.Set.empty[String]
    val poll = collection.mutable.Map.empty[String, Int]

    override def persistenceId: String = "simple-voting"
    override def receiveCommand: Receive = {
      case event: Vote =>
        if (!votedSet.contains(event.citizenPID)) {
          log.info(s"${event.citizenPID} voting")
          persist(event) {e =>
            stateChange(event)
          }
        } else log.error("Already voted")
      case Poll => println(poll)
    }
    override def receiveRecover: Receive = {
      case event: Vote =>
        log.info(s"${event.citizenPID} voting")
        stateChange(event)
    }

    def stateChange(vote: Vote) = {
      votedSet.add(vote.citizenPID)
      poll(vote.candidate) = poll.getOrElse(vote.candidate, 0) + 1
    }
  }

  val system = ActorSystem("voting-actors")
  val votingActor = system.actorOf(Props[VotingActor])
  //votingActor ! Vote("1", "Bob")
  //votingActor ! Vote("2", "Bob")
  //votingActor ! Vote("3", "Alice")

  votingActor ! Poll

}
