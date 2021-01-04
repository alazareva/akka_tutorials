package clustering.clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberJoined, MemberRemoved, MemberUp, UnreachableMember}
import com.typesafe.config.ConfigFactory

class ClusterSubscriber extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    )
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case MemberJoined(member) =>
      log.info(f"New member in town ${member.address}")
    case MemberUp(member) if member.hasRole("numberCruncher") =>
      log.info(s"Hello numberCruncher ${member.address}")
    case MemberUp(member) =>
      log.info(f"Welcome new member ${member.address}")
    case MemberRemoved(member, previousStatus) =>
      log.info(f"Poor${member.address} was removed from $previousStatus")
    case UnreachableMember(member) =>
      log.info(f"Uh oh ${member.address} is unreachable")
    case m: MemberEvent =>
      log.info(s"Another member event $m")
  }
}

object ClusteringBasics extends App {

  def startCluster(ports: List[Int]): Unit = {
    ports.foreach { port =>
      val config = ConfigFactory.parseString(
        s"""
          |akka.remote.artery.canonical.port = $port
        """.stripMargin
      ).withFallback(ConfigFactory.load("clustering/clusteringBasics.conf"))

      val system = ActorSystem("cluster", config) // all actor systems in cluster must have same name
      system.actorOf(Props[ClusterSubscriber], "subscriber")

    }
  }

  startCluster(List(2551, 2552, 0)) // 0 will allocate port

}

object ClusteringBasics_Manual extends App {

  val system = ActorSystem("cluster", ConfigFactory.load("clustering/clusteringBasics.conf").getConfig("manualRegistration"))
  val cluster = Cluster(system)
  cluster.joinSeedNodes(List(
    Address("akka", "cluster", "localhost", 2551),
    Address("akka", "cluster", "localhost", 2552)
  ))
  system.actorOf(Props[ClusterSubscriber], "subscriber")
}
