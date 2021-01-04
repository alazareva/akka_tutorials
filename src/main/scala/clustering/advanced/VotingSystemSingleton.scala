package clustering.advanced

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, ReceiveTimeout}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

case class Person(id: String, age: Int)

object Person {
  def generate() = Person(UUID.randomUUID.toString, 16 + Random.nextInt(90))
}

case class Vote(person: Person, candidate: String)

case object VoteAccepted

case class VoteRejected(reason: String)

class VotingAggregator extends Actor with ActorLogging {
  val CANDIDATES = Set("martin", "roland", "jonas", "daniel")

  override def receive: Receive = online(Set(), Map())

  def offline: Receive = {
    case v: Vote =>
      log.warning(s"Got $v after polls closed")
      sender() ! VoteRejected("Polls closed")
    case m =>
      log.warning(s"Message $m after polls closed")
  }

  context.setReceiveTimeout(60 seconds)

  def online(personsVoted: Set[String], polls: Map[String, Int]): Receive = {
    case Vote(Person(id, age), candidate) =>
      if (personsVoted.contains(id)) sender() ! VoteRejected("Already voted")
      else if (age < 18) sender() ! VoteRejected("Too young")
      else if (!CANDIDATES.contains(candidate)) sender() ! VoteRejected("Invalid candidate")
      else {
        log.info(s"Recording vote for $candidate")
        val votes = polls.getOrElse(candidate, 0)
        sender() ! VoteAccepted
        context.become(online(personsVoted + id, polls + (candidate -> (votes + 1))))
      }
    case ReceiveTimeout =>
      log.info(s"POLL RESULTS $polls")
      context.setReceiveTimeout(Duration.Undefined)
      context.become(offline)
  }
}

class VotingStation(votingAggregator: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case v: Vote => votingAggregator ! v
    case VoteAccepted => log.info("Vote accepted")
    case VoteRejected(reason) => log.warning(s"Vote rejected $reason")
  }
}

object VotingStation {
  def props(votingAggregator: ActorRef) = Props(new VotingStation(votingAggregator))
}

object CentralElectionSystem extends App {

  def startNode(port: Int) {
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port = $port
    """.stripMargin
    ).withFallback(ConfigFactory.load("clustering/votingSystemSingleton.conf"))

    val system = ActorSystem("cluster", config)

    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props[VotingAggregator],
        terminationMessage = PoisonPill,
        ClusterSingletonManagerSettings(system)
      ),
      "votingAggregator"
    )
  }

  (2551 to 2553).foreach(startNode)
}

class VotingStationApp(port: Int) extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = $port
    """.stripMargin
  ).withFallback(ConfigFactory.load("clustering/votingSystemSingleton.conf"))

  val system = ActorSystem("cluster", config)

  val proxy = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/votingAggregator",
      settings = ClusterSingletonProxySettings(system),
    ),
    "pollingStationProxy"
  )

  val votingStation = system.actorOf(VotingStation.props(proxy))

  scala.io.Source.stdin.getLines().foreach { candidate =>

    votingStation ! Vote(Person.generate(), candidate)
  }
}


object Washington extends VotingStationApp(2561)
object NYC extends VotingStationApp(2562)