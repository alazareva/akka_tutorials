package testingactors

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.util.Random
import scala.concurrent.duration._

object TimedAssertionsSpec {

  case class WorkRes(x: Int)

  class WorkerActor extends Actor {
    override def receive: Receive = {
      case "work" =>
        Thread.sleep(500)
        sender() ! WorkRes(42)
      case "workSeq" =>
        val r = new Random()
        (1 to 10).foreach(_ => {
          Thread.sleep(r.nextInt(50))
          sender() ! WorkRes(1)
        })
    }
  }

}

class TimedAssertionsSpec extends
  TestKit(ActorSystem("timedSpec", ConfigFactory.load("specialTimedAssertionsConfig"))) with
  ImplicitSender with
  WordSpecLike with
  BeforeAndAfterAll {

  import TimedAssertionsSpec._

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])

    "reply in a timely manner" in {
      within(500 millis, 1 second) {
        workerActor ! "work"
        expectMsg(WorkRes(42))
      }
    }
    "reply with cadence" in {
      within(1 second) {
        workerActor ! "workSeq"
        val res = receiveWhile[Int](max = 2 seconds, idle = 500 millis, messages = 10) {
          case WorkRes(result) => result
        }
        assert(res.sum > 5)
      }
    }

    "reply to test probe" in {
      within(1 second) {
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(WorkRes(42))
      }
    }
  }

}
