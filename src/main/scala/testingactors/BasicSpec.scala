package testingactors

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Random

class BasicSpec extends TestKit(ActorSystem("basicSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  import BasicSpec._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  // the messages are sent by testActor implicit

  "a simple actor" should {
    "send back the same message" in {
      val actor = system.actorOf(Props[SimpleActor])
      val message = "hello"
      actor ! message

      expectMsg(message)

    }
  }

  "a black hole actor" should {
    "don't return anything" in {
      val actor = system.actorOf(Props[BlackHole])
      val message = "hello" // times out in 3 seconds
      actor ! message

      expectNoMessage(1 second)
    }
  }

  // message assertions

  "a lab test actor" should {
    val actor = system.actorOf(Props[LabTestActor])
    "turn string to uppercase" in {
      val message = "hello" // times out in 3 seconds
      actor ! message
      val reply = expectMsgType[String]
      assert(reply == "HELLO")
    }

    "turn reply to hi" in {
      val message = "hi"
      actor ! message
      expectMsgAnyOf("hi", "hello")
    }

    "turn reply to tech" in {
      val message = "tech"
      actor ! message
      expectMsgAllOf("scala", "akka")
    }

    "turn reply to tech again" in {
      val message = "tech"
      actor ! message
      val msgs = receiveN(2) // Seq Any
      // to assertions
    }

    "turn reply to tech again again" in {
      val message = "tech"
      actor ! message
      expectMsgPF() {
        case "scala" =>
        case "akka" =>
      }
    }
  }


}

object BasicSpec {
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case message => sender() ! message
    }
  }

  class BlackHole extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {
    val random = new Random()
    override def receive: Receive = {
      case "tech" =>
        sender() ! "scala"
        sender() ! "akka"
      case "hi" => if (random.nextBoolean) sender() ! "hi" else sender() ! "hello"
      case message: String => sender() ! message.toUpperCase
    }
  }
}
