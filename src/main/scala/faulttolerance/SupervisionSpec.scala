package faulttolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{CallingThreadDispatcher, EventFilter, ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

object SupervisionSpec {

  case object Report

  class FussyWordCounter extends Actor {
    var words = 0

    override def receive: Receive = {
      case Report => sender() ! words
      case "" => throw new NullPointerException("empty string")
      case text: String =>
        if (text.length > 20) throw new RuntimeException("too long")
        else if (!Character.isUpperCase(text(0))) throw new IllegalArgumentException("must be upper case")
        else words += text.split(" ").length
      case _ => throw new Exception("bad message")
    }
  }

  class Supervisor extends Actor {
    override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }

    override def receive: Receive = {
      case props: Props =>
        val child = context.actorOf(props)
        sender() ! child
    }
  }

  class NoDeathOnRestart extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {}
  }

  class AllForOneSupervisor extends Supervisor {
    override val supervisorStrategy: SupervisorStrategy = AllForOneStrategy() { // applies to all
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }
  }

}


class SupervisionSpec extends
  TestKit(ActorSystem("supervisionSpec")) with
  ImplicitSender with
  WordSpecLike with
  BeforeAndAfterAll {

  import SupervisionSpec._


  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "a supervisor" should {
    "resume its child if RuntimeException" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I like akka"
      child ! Report
      expectMsg(3)
      child ! "Amx" * 50
      child ! Report
      expectMsg(3)
    }

    "restart if null pointer" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I like akka"
      child ! Report
      expectMsg(3)
      child ! ""
      child ! Report
      expectMsg(0)
    }

    "terminate if illegal arg" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)
      child ! "akka"
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }

    "escalate when it doesn't know what to do" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)
      child ! 2
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }
  }

  "a no restart supervisor" should {
    "not kill children on restart" in {
      val supervisor = system.actorOf(Props[NoDeathOnRestart], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]
      child ! "Akka is cool"
      child ! Report
      expectMsg(3)
      child ! 45
      child ! Report
      expectMsg(0) // got restarted
    }
  }

  "an all for one supervisor" should {
    "apply the all for one strategy" in {
      val supervisor = system.actorOf(Props[AllForOneSupervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child1 = expectMsgType[ActorRef]
      supervisor ! Props[FussyWordCounter]
      val child2 = expectMsgType[ActorRef]
      child2 ! "Hi there"
      child2 ! Report
      expectMsg(2)

      EventFilter[NullPointerException]() intercept {
        child1 ! ""
      }
      Thread.sleep(500)
      child2 ! Report
      expectMsg(0)
    }
  }

}
