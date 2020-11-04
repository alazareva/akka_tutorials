package testingactors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

object TestProbeSpec {
  // word counting master - worker

  // send work to master
  // master sends work to worker
  // worker responds to master
  // master responds to sender
  case class Register(worker: ActorRef)
  case class Work(text: String)
  case class WorkerWork(text: String, orgSender: ActorRef)
  case class WorkCompleted(count: Int, orgSender: ActorRef)
  case class Report(count: Int)
  case object RegisterAk

  class Master extends Actor {
    override def receive: Receive = {
      case Register(worker) => {
        sender() ! RegisterAk
        context.become(receiveWithWorker(worker))
      }
    }

    def receiveWithWorker(worker: ActorRef, totalWordCount: Int = 0): Receive = {
      case Work(text) => worker ! WorkerWork(text, sender())
      case WorkCompleted(c, s) =>
        val newCount = totalWordCount + c
        s ! Report(newCount)
        context.become(receiveWithWorker(worker, newCount))
    }
  }

}

class TestProbeSpec extends TestKit(ActorSystem("probeSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  import TestProbeSpec._

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "a master"  should {
    "register a worker" in {
      val master = system.actorOf(Props[Master])
      val worker = TestProbe("worker")

      master ! Register(worker.ref)
      expectMsg(RegisterAk)
    }
    "send the work to the worker" in {
      val master = system.actorOf(Props[Master])
      val worker = TestProbe("worker")
      master ! Register(worker.ref)
      expectMsg(RegisterAk)
      val workString = "akka"
      master ! Work(workString)
      worker.expectMsg(WorkerWork(workString, testActor))
      worker.reply(WorkCompleted(1, testActor))
      expectMsg(Report(1)) // testActor is implicit
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val worker = TestProbe("worker")
      master ! Register(worker.ref)
      expectMsg(RegisterAk)
      val workString = "akka"
      master ! Work(workString)
      master ! Work(workString)

      worker.receiveWhile() {
        case WorkerWork(`workString`, `testActor`) => worker.reply(WorkCompleted(1, testActor))
      }
      expectMsg(Report(1)) // testActor is implicit
      expectMsg(Report(2))
    }
  }





}
