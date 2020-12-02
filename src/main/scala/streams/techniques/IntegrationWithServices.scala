package streams.techniques

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import scala.concurrent.Future

object IntegrationWithServices extends App {


  implicit val system = ActorSystem("integrating-with-services")
  implicit val materializer = ActorMaterializer()
  // import system.dispatcher // not recommended for mapAsync
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericExternalService[A, B](element: A): Future[B] = ???

  // example simplified pager duty

  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("infa", "broke", new Date),
    PagerEvent("pipline", "broke", new Date),
    PagerEvent("infa", "stopped", new Date),
    PagerEvent("front end", "button broke", new Date),
  ))

  object PagerService {
    private val engineers = List("Bob", "Alice")
    private val emails = Map(
      "Bob" -> "bob@gmail.com",
      "Alice" -> "alice@gmail.com",
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val engIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
      val engineer = engineers(engIndex.toInt)
      val email = emails(engineer)
      println(s"Sending page to $email")
      Thread.sleep(1000)
      email
    }
  }

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Bob", "Alice")
    private val emails = Map(
      "Bob" -> "bob@gmail.com",
      "Alice" -> "alice@gmail.com",
    )

    def processEvent(pagerEvent: PagerEvent): String = {
      val engIndex = pagerEvent.date.toInstant.getEpochSecond / (24 * 3600) % engineers.length
      val engineer = engineers(engIndex.toInt)
      val email = emails(engineer)
      println(s"Sending page to $email")
      Thread.sleep(1000)
      email
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  val infraEvents = eventSource.filter(_.application == "infa")
  val pagerEmails = infraEvents.mapAsync(parallelism = 4)(PagerService.processEvent)
  // map async guarantees relative order of elements
  val pagedEmailSink = Sink.foreach[String](email => println(s"sent to $email"))

  pagerEmails.to(pagedEmailSink).run()

  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  implicit val timeout = Timeout(2 seconds)
  val alternative = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternative.to(pagedEmailSink).run()
}
