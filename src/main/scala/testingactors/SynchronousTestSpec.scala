package testingactors

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.Duration

class SynchronousTestSpec extends WordSpecLike with BeforeAndAfterAll {
  import SynchronousTestSpec._

  implicit val system = ActorSystem("syncTest")

  "a counter" should {
    "synchronously increase count" in {

      val counter = TestActorRef[Counter](Props[Counter])
      counter ! Inc // counter has already received the message, single thread
      assert(counter.underlyingActor.count == 1)
    }

    "synchronously increase counter in call of receive" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter.receive(Inc)
      assert(counter.underlyingActor.count == 1)
    }

    "work on the calling thread dispatcher" in {
      val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
      val probe = TestProbe()
      probe.send(counter, Read)
      probe.expectMsg(Duration.Zero, 0) // already received because of calling thread dispatcher
    }
  }


  override def afterAll(): Unit = system.terminate()

}

object SynchronousTestSpec {

  case object Inc
  case object Read
  class Counter extends Actor {
    var count = 0

    override def receive: Receive = {
      case Inc => count += 1
      case Read => sender() ! count
    }

  }
}