package faulttolerance

import java.io.File

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import scala.io.Source
import scala.util.{Success, Try}

object BackoffSupervisorPattern extends App {

  case object ReadFile

  class FileBasedPersistentActor extends Actor with ActorLogging {

    override def preStart(): Unit =
      log.info("actor starting")

    override def postStop(): Unit =
      log.warning("actor stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.warning("actor restarting")

    var dataSource: Source = null

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null)
          dataSource = Source.fromFile(new File("src/main/resources/testFiles/important_data.txt"))
        log.info("Read some data " + dataSource.getLines().toList)
    }
  }

  class EagerFileBasedPersistentActor extends FileBasedPersistentActor {
    override def preStart(): Unit = {
      log.info("eager actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/testFiles/important_data.txt"))
    }
  }

  val system = ActorSystem("backoff")
  val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
  // simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      Props[FileBasedPersistentActor],
      "simpleBackoffActor",
      2 seconds,
      30 seconds,
      0.2
    )
  )

  // val simpleBackOffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
  // simpleSupervisor -> simpleBackoffActor is the child
  // supervision strategy is default
  // first attempt in 3 seconds, then 6 then 12 etc... till 30 with some noise
  //simpleBackOffSupervisor ! ReadFile

  val stopSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[FileBasedPersistentActor],
      "stopBackoffActor",
      2 seconds,
      30 seconds,
      0.2
    ).withSupervisorStrategy(
      OneForOneStrategy() {
        case _ => Stop
      }
    )
  )

  //val simpleStopOffSupervisor = system.actorOf(simpleSupervisorProps, "stopSupervisor")
  //simpleStopOffSupervisor ! ReadFile

  // val eagerActor = system.actorOf(Props[EagerFileBasedPersistentActor])
  // if child actor throws initialization exception => STOP

  val repeatedSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[EagerFileBasedPersistentActor],
      "eagerActor",
      1 second,
      30 seconds,
      0.1
    )
  )

  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "repeatedSupervisor")
  // repeatedSupervisor -> eagerActor, child will die and trigger supervision strategy
  // after 1 second the backoff will kick in

}
