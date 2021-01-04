package clustering.remoting

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, AddressFromURIString, Deploy, PoisonPill, Props, Terminated}
import akka.remote.RemoteScope
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

object DeployingRemotely_Local extends App {

  val system = ActorSystem("local", ConfigFactory.load("remoting/deployingRemotely.conf").getConfig("localApp"))

  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor")
  simpleActor ! "Hello remote actor"

  println(simpleActor) // Actor[akka://remote@localhost:2552/remote/akka/local@localhost:2551/user/remoteActor#685942170]

  // programmatic

  val remoteSystemAddress: Address = AddressFromURIString("akka://remote@localhost:2552")
  val remoteActor = system.actorOf(
    Props[SimpleActor].withDeploy(Deploy(scope = RemoteScope(remoteSystemAddress)))
  )

  remoteActor ! "Hi remote actor"

  // remote routees
  val poolRouter = system.actorOf(FromConfig.props(Props[SimpleActor]), "router")
  (1 to 10).map(i => s"message $i").foreach(m => poolRouter ! m)

  // watching remote actors

  class ParentActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case "create" =>
        log.info("creating child")
        val child = context.actorOf(Props[SimpleActor], "remoteChild")
        context.watch(child)
      case Terminated(ref) =>
        log.warning(s"Terminated $ref")
    }
  }

  val parent = system.actorOf(Props[ParentActor], "watcher")

  parent ! "create"

 Thread.sleep(1000)
 system.actorSelection(
   "akka://remote@localhost:2552/remote/akka/local@localhost:2551/user/watcher/remoteChild") ! PoisonPill

  // actor systems send heartbeat messages

}


object DeployingRemotely_Remote extends App {

  val system = ActorSystem("remote", ConfigFactory.load("remoting/deployingRemotely.conf").getConfig("remoteApp"))

}
