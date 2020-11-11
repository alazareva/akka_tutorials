package akkaessentials.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Dispatchers extends App {

  class Counter extends Actor with ActorLogging {
    var count = 0

    override def receive: Receive = {
      case message =>
        count += 1
        log.info(f"[$message] $count")
    }
  }

  val system = ActorSystem("dispatchersDemo", ConfigFactory.load().getConfig("dispatchersDemo"))

  // method 1, in code
  val actors = (1 to 10).map(i =>
    system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), f"actor-$i")
  )

  val r = new Random()
  (1 to 100).foreach{ i =>
   // actors(r.nextInt(10)) ! i
  }

  // method 2, config
  val actor2 = system.actorOf(Props[Counter], "rtjvm")

  // dispatchers implement the ExecutionContext trait
  // using futures is discouraged in Actors but with blocking ops you might starve the dispatcher
  // so use a separate execution context
  class DBActor extends Actor with ActorLogging {
    implicit val ec: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher")
    // or use a router
    override def receive: Receive = {
      case message => Future {
        Thread.sleep(5000)
        log.info(f"Success $message")
      }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
  val nonBlocking =  system.actorOf(Props[Counter])
  //dbActor ! "hi"

  (1 to 100).foreach { i =>
    val message = f"message $i"
    dbActor ! message
    nonBlocking ! message
  }

  // there are different types of dispatchers, Dispatcher has a thread pool, Pinned
  // has one thread per actor, CallingThreadDispatcher

}
