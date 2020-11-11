package akkaessentials.advancedactors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AskSpec {

  case class Read(key: String)

  case class Write(Key: String, value: String)

  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map.empty[String, String])

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(f"getting for key $key")
        sender() ! kv.get(key)
      case Write(k, v) =>
        log.info(f"writing $k -> $v")
        context.become(online(kv + (k -> v)))
    }
  }

  case class RegisterUser(username: String, password: String)

  case class Authenticate(username: String, password: String)

  case class AuthFailure(message: String)

  case object AuthSuccess

  class AuthManager extends Actor with ActorLogging {
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val ec: ExecutionContext = context.dispatcher
    val authDB = context.actorOf(Props[KVActor])

    override def receive: Receive = {
      case RegisterUser(u, p) => authDB ! Write(u, p)
      case Authenticate(u, p) => handleAuthentication(u, p)

    }

    def handleAuthentication(username: String, password: String): Unit = {
      val future = authDB ? Read(username) // step 2, runs of separate thread
      val originalSender = sender() // do not close over actor instance
      future.onComplete { // on a different thread
        // NEVER call methods on actor instance or access mutable state onComplete
        case Success(None) => originalSender ! AuthFailure("user not found")
        case Success(Some(pw)) =>
          if (pw == password) originalSender ! AuthSuccess
          else originalSender ! AuthFailure("wrong pw")
        case Failure(_) => originalSender ! AuthFailure(f"error ")
      }
    }
  }

  class PipedManager extends AuthManager {
    override def handleAuthentication(username: String, password: String): Unit = {
      val future = authDB ? Read(username) // Future[Any]
      val passwordFuture = future.mapTo[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure("user not found")
        case Some(dbPw) =>
          if (dbPw == password) AuthSuccess else AuthFailure("wrong pw")
      } // Future[Any]
      responseFuture.pipeTo(sender()) // when future completes send response to sender
    }
  }
}

class AskSpec extends
  TestKit(ActorSystem("interceptingLogsSpec")) with
  ImplicitSender with
  WordSpecLike with
  BeforeAndAfterAll {

  import AskSpec._

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  def authenticatorTestSuite(props: Props) = {
      "fail to authenticate" in {
        val authManager = system.actorOf(props)
        authManager ! Authenticate("user", "bad")
        expectMsg(AuthFailure("user not found"))
      }

      "fail to authenticate if invalid pw" in {
        val authManager = system.actorOf(props)
        authManager ! RegisterUser("asiya", "hi")
        authManager ! Authenticate("asiya", "bad")
        expectMsg(AuthFailure("wrong pw"))
      }
  }

  "an authenticator" should {
    authenticatorTestSuite(Props[AuthManager])
  }
    "piped authenticator" should {
      authenticatorTestSuite(Props[PipedManager])
    }
  }


