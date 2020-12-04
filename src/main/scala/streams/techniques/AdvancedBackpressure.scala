package streams.techniques

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object AdvancedBackpressure extends App {

  implicit val system = ActorSystem("advanced-backpressure")
  implicit val materializer = ActorMaterializer()

  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("service failed", new Date),
    PagerEvent("illegal elements", new Date),
    PagerEvent("server non-responsive", new Date)
  )

  val eventSource = Source(events)

  val oncallEngineer = "bob@gmail.com"

  def sendEmail(notification: Notification) = println(s"${notification.email} has event")

  val notificationSink = Flow[PagerEvent].map(event => Notification(oncallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(s"${notification.email} has event ${notification.pagerEvent}")
  }

  // aggregate things for slow consumers

  val aggregateNotificationFlow = Flow[PagerEvent].conflate((event1, event2) => {
    val instances = event1.nInstances + event2.nInstances
    PagerEvent(s"You have $instances events", new Date, instances)
  }).map(e => Notification(oncallEngineer, e))

  // eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlow)).run()

  // for slow producer, extrapolate/expend

  import scala.concurrent.duration._

  val slowCounter = Source(Stream.from(1)).throttle(1, 1 second)
  val fastSink = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))

  // slowCounter.via(repeater).to(fastSink).run()

  val expander = Flow[Int].expand(element => Iterator.continually(element)) // always produces not just when unmet demand

}
