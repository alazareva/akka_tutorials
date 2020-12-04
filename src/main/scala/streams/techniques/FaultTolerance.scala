package streams.techniques

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.{Sink, Source}

import scala.util.Random

object FaultTolerance extends App {

  implicit val system = ActorSystem("fault-tolerance")
  implicit val materializer = ActorMaterializer()

  val faultySource = Source(1 to 10).map(e => if (e == 6) throw new RuntimeException else e)
  // faultySource.log("trackingElement").to(Sink.ignore).run()

  faultySource.recover {
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource")

  // recover with another stream

  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  }).log("recoverWithRetries").to(Sink.ignore)//.run()

  // backoff supervision
  import scala.concurrent.duration._
  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1 second,
    maxBackoff = 30 seconds,
    randomFactor = 0.2,
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 20).map(em => if (em == randomNumber) throw new RuntimeException else em)
  }).log("restartBackoff").to(Sink.ignore)//.run()

  // supervision strategy

  val numbers = Source(1 to 20).map(e => if (e == 13) throw new RuntimeException else e).log("supervision")

  numbers.withAttributes(ActorAttributes.supervisionStrategy {
    // Resume - skips bad element, Stop , Restart - resume but clears internal state
    case _: RuntimeException => Resume
    case _ => Stop
  }).to(Sink.ignore).run()



}
