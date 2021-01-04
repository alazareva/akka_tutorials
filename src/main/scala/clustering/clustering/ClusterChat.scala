package clustering.clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import akka.pattern.pipe

object ChatDomain {
  case class ChatMessage(nickname: String, contents: String)
  case class UserMessage(context: String)
  case class EnterRoom(fullAddress: String, nickname: String)
}

object ChatActor {
  def props(nickname: String, port: Int) = Props(new ChatActor(nickname, port))
}

class ChatActor(nickname: String, port: Int) extends Actor with ActorLogging {
  import ChatDomain._
  // todo init cluster object
  // todo subscribe to cluser events in prestart
  // todo unsub self in post stop

  import context.dispatcher
  implicit val timeout: Timeout = Timeout(3 seconds)


  val cluster = Cluster(context.system)
  override def postStop(): Unit = cluster.unsubscribe(self)

  var chatRoom: Map[String, String] = Map.empty[String, String]

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
    )
  }

  override def receive: Receive = {
    case MemberUp(member) =>
      val workerSelection = getChatActor(member.address.toString)
      workerSelection ! EnterRoom(s"${self.path.address}@localhost:$port", nickname)
    case MemberRemoved(member, _) =>
      log.info(s"${chatRoom(member.address.toString)} left chat")
      chatRoom -= member.address.toString
    case EnterRoom(remoteAddress, remoteNickname) =>
      if (remoteNickname != nickname) {
        log.info(s"$remoteNickname entered chat")
        chatRoom += (remoteAddress -> remoteNickname)
      }
    case UserMessage(content) =>
      chatRoom.keys.foreach { addr =>
        val workerSelection = getChatActor(addr)
        workerSelection ! ChatMessage(nickname, content)
      }
    case ChatMessage(remoteNickname, contents) =>
      log.info(s"$remoteNickname said $contents")
  }

  def getChatActor(memberAddress: String) = context.actorSelection(s"$memberAddress/user/chatActor")
}

class ChatApp(nickname: String, port: Int) extends App {
  import ChatDomain._
  val config = ConfigFactory.parseString(
    s"""
      |akka.remote.artery.canonical.port = $port
    """.stripMargin
  ).withFallback(ConfigFactory.load("clustering/clusterChat.conf"))
  val system = ActorSystem("cluster", config)
  val chatActor = system.actorOf(ChatActor.props(nickname, port), "chatActor")

  scala.io.Source.stdin.getLines().foreach { line =>
    chatActor ! UserMessage(line)
  }
}

object Alice extends ChatApp("Alice", 2551)
object Bob extends ChatApp("Bob", 2552)
object Charlie extends ChatApp("Charlie", 2553)