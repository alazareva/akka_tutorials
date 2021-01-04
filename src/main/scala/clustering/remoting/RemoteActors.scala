package clustering.remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorSystem, Identify, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object RemoteActors extends App {

  val localSystem = ActorSystem("local", ConfigFactory.load("remoting/remote_actors.conf"))
  val localActor = localSystem.actorOf(Props[SimpleActor], "localSimple")
  localActor ! "hello local actor"

  import localSystem.dispatcher

  // send message to remote actor
  // Method 1 actor selection

  val remoteActorSelection = localSystem.actorSelection("akka://remote@localhost:2552/user/remoteSimple")

  remoteActorSelection ! "hello from local jvm"

  // method 2 actor ref
  implicit val timout: Timeout = Timeout(3 seconds)
  val remoteActorFuture = remoteActorSelection.resolveOne()
  remoteActorFuture.onComplete {
    case Success(ref) => ref ! "I got the actor"
    case Failure(exception) => println(s"Failed $exception")
  }

  // method 3 via messages

  class ActorResolver extends Actor with ActorLogging {
    /*
    ask for an actor selection from local system
    resolver send an Identify(id) to actor selection
    remote actor will respond with ActorIdentity(id, actorRef)
    then actor resolver is free to use remote actor ref
     */

    override def preStart(): Unit = {
      val selection = context.actorSelection("akka://remote@localhost:2552/user/remoteSimple")
      selection ! Identify(42)
    }

    override def receive: Receive = {
      case ActorIdentity(42, Some(ref)) => ref ! "Thank you for resolving"
    }
  }

  localSystem.actorOf(Props[ActorResolver], "resolver")

}

object RemoteActors_Remote extends App {
  val remoteSystem = ActorSystem("remote", ConfigFactory.load("remoting/remote_actors.conf").getConfig("remoteSystem"))
  val remoteActor = remoteSystem.actorOf(Props[SimpleActor], "remoteSimple")
  remoteActor ! "hello remote actor"
}
