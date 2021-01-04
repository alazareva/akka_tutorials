package clustering.clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.util.Timeout

import scala.concurrent.duration._
import akka.pattern.pipe
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.Random

object ClusteringExampleDomain {
  case class ProcessFile(filename: String)
  case class ProcessLine(line: String, aggregator: ActorRef)
  case class ProcessLineResult(count: Int)
}


class ClusterWordCountPriorityMailbox(settings: ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox (
  PriorityGenerator {
    case _: MemberEvent => 0
    case _ => 1
  })


class Master extends Actor with ActorLogging {
  import ClusteringExampleDomain._

  import context.dispatcher
  implicit val timeout: Timeout = Timeout(3 seconds)

  val cluster = Cluster(context.system)

  var workers: Map[Address, ActorRef] = Map()
  var pendingRemoval: Map[Address, ActorRef] = Map()

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    )
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = handleClusterEvents orElse handleWorkerRegistration orElse handleJob

  def handleClusterEvents: Receive = {
    case MemberUp(member) if member.hasRole("worker") =>
      log.info(s"Member up ${member.address}")
      if (pendingRemoval.contains(member.address)) {
        pendingRemoval = pendingRemoval - member.address
      } else {
        val workerSelection = context.actorSelection(s"${member.address}/user/worker")
        workerSelection.resolveOne().map(ref => (member.address, ref)).pipeTo(self)
      }

    case UnreachableMember(member) if member.hasRole("worker") =>
      log.info(s"Member unreachable ${member.address}")
      val workerOption = workers.get(member.address)
      workerOption.foreach { ref =>
        pendingRemoval = pendingRemoval + (member.address -> ref)
      }

    case MemberRemoved(member, previousStatus) if member.hasRole("worker") =>
      log.info(s"Member removed ${member.address} with status $previousStatus")
      workers = workers - member.address

    case m: MemberEvent =>
      log.info(s"Member event $m")

  }

  def handleWorkerRegistration: Receive = {
    case pair: (Address, ActorRef) =>
      log.info(s"Registering $pair")
      workers = workers + pair
  }

  def handleJob: Receive = {
    case ProcessFile(filename) =>
      val aggregator = context.actorOf(Props[Aggregator], "aggregator")
      scala.io.Source.fromFile(filename).getLines().foreach { line =>
      self ! ProcessLine(line, aggregator)
      }
    case ProcessLine(line, aggregator) => {
      val workerIndex = Random.nextInt((workers -- pendingRemoval.keys).size)
      val worker = (workers -- pendingRemoval.keys).values.toSeq(workerIndex)
      log.info(s"Sending $line to $worker")
      worker ! ProcessLine(line, aggregator)
      Thread.sleep(10)
    }
  }

}

class Aggregator extends Actor with ActorLogging {
  import ClusteringExampleDomain._

  context.setReceiveTimeout(15 seconds)

  override def receive: Receive = online(0)

  def online(totalCount: Int): Receive = {
    case ProcessLineResult(count) =>
      context.become(online(totalCount + count))

    case ReceiveTimeout =>
      log.info(f"TOTAL COUNT $totalCount")
      context.setReceiveTimeout(Duration.Undefined)
  }
}

class Worker extends Actor with ActorLogging {
  import ClusteringExampleDomain._

  override def receive: Receive = {
    case ProcessLine(line, aggregator) =>
      log.info(s"Processing line $line")
      aggregator ! ProcessLineResult(line.split(" ").length)
    case m => log.info(s"MESSAGE $m")
  }
}

object SeedNodes extends App {
  import ClusteringExampleDomain._

  def createNode(port: Int, role: String, props: Props, actorName: String): ActorRef = {
    val config = ConfigFactory.parseString(
      s"""
        |akka.cluster.roles = ["$role"]
        |akka.remote.artery.canonical.port = $port
      """.stripMargin
    ).withFallback(ConfigFactory.load("clustering/clusteringExample.conf"))

    val system = ActorSystem("cluster", config)
    system.actorOf(props, actorName)
  }

  val master = createNode(2551, "master", Props[Master], "master")
  val worker1 = createNode(2552, "worker", Props[Worker], "worker")
  val worker2 = createNode(2553, "worker", Props[Worker], "worker")

  Thread.sleep(10000)

  master ! ProcessFile("src/main/resources/txt/lipsum.txt")
}

object AdditionalWorker extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.cluster.roles = ["worker"]
       |akka.remote.artery.canonical.port = 2554
      """.stripMargin
  ).withFallback(ConfigFactory.load("clustering/clusteringExample.conf"))

  val system = ActorSystem("cluster", config)
  system.actorOf(Props[Worker], "worker")

}
