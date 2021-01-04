package clustering.remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSystem, Identify, PoisonPill, Props}
import com.typesafe.config.ConfigFactory


object WordCountDomain {

  case class Initialize(nWorkers: Int)

  case class WordCountTask(text: String)

  case class WordCountResult(count: Int)

  case object EndWordCount

}

class WordCountWorker extends Actor with ActorLogging {

  import WordCountDomain._

  override def receive: Receive = {
    case WordCountTask(text) =>
      log.info(s"Processing $text")
      sender() ! WordCountResult(text.split("").length)
  }

}

class WordCountMaster extends Actor with ActorLogging {

  import WordCountDomain._

  override def receive: Receive = {
    case Initialize(nWorkers) =>
      // deploy them remotely
      (1 to nWorkers).foreach {i =>
        val child = context.actorOf(Props[WordCountWorker], s"worker$i")
        child ! Identify(i)
      }
      context.become(initializing(List.empty[ActorRef], nWorkers))
  }

  def initializing(workers: List[ActorRef], remainingWorkers: Int): Receive = {
    case ActorIdentity(i, Some(ref)) =>
      if (remainingWorkers == 1) context.become(online(ref :: workers, 0, 0))
      else context.become(initializing(ref :: workers, remainingWorkers - 1))
  }

  def online(workers: List[ActorRef], remainingTasks: Int, totalCount: Int): Receive = {
    case text: String =>
      val sentences = text.split("\\. ")
      Iterator.continually(workers).flatten.zip(sentences.iterator).foreach { case (ref, sent) =>
        ref ! WordCountTask(sent)
      }
      context.become(online(workers, remainingTasks + sentences.length, totalCount))
    case WordCountResult(count) =>
      if (remainingTasks == 1) {
        log.info(s"Total res = ${totalCount + count}")
        workers.foreach(_ ! PoisonPill)
        context.stop(self)
      } else {
        context.become(online(workers, remainingTasks - 1, totalCount + count))
      }
  }
}

object RemoteActorsEx extends App {

  import WordCountDomain._

  val config = ConfigFactory.parseString(
   """
     |akka.remote.artery.canonical.port = 2551
   """.stripMargin
  ).withFallback(ConfigFactory.load("remoting/remoteActorsEx.conf"))

  val system = ActorSystem("master", config)

  val master = system.actorOf(Props[WordCountMaster], "wordCountMaster")

  master ! Initialize(5)

  Thread.sleep(1000)

  scala.io.Source.fromFile("src/main/resources/txt/lipsum.txt").getLines().foreach { line =>
    master ! line
  }

}

object WorkersApp extends App {
  import WordCountDomain._

  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2552
    """.stripMargin
  ).withFallback(ConfigFactory.load("remoting/remoteActorsEx.conf"))

  val system = ActorSystem("workers", config)
}
