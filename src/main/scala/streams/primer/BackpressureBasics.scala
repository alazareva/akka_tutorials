package streams.primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object BackpressureBasics extends App {

  implicit val system = ActorSystem("backpressure-basics")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000)
    println(s"sink $x")
  }

  // fastSource.to(slowSink).run() // this is fusing, not backbressure

  // but
  // fastSource.async.to(slowSink).run() // backpressure

  val simpleFlow = Flow[Int].map { x =>
    println(s"flow $x")
    x + 1
  }

  // fastSource.async.via(simpleFlow).async.to(slowSink).run()
  /*
  component will react with
  - try to slow down production of elements
  - buffer elements
  - droop down elements from buffer if overflows
  - kills the whole stream and fail
   */

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)

  fastSource.async.via(bufferedFlow).async.to(slowSink).run()

  /*
  dropHead
  1 - 16 noone backpressured
  17 - 26 flow will start dropping at the next element
  26 - 1000, flow will always drop the oldest element
  remaining is 991 - 1001 => Sink
   */

  // throttling
  import scala.concurrent.duration._
  fastSource.throttle(2, 1 second).runWith(Sink.foreach(println))
}
