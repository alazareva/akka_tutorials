package streams.advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.duration._

object DynamicStreamHandling extends App {

  implicit val system = ActorSystem("dynamic-streams")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // Kill switch
  val killSwitchFlow = KillSwitches.single[Int]
  val sharedKillSwitch = KillSwitches.shared("shared")

  val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
  val counter2 = Source(Stream.from(1)).throttle(2, 1 second).log("counter2")


  val sink = Sink.ignore

  val killSwitch = counter
    .viaMat(killSwitchFlow)(Keep.right)
    .to(sink)
    //.run()

  //counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  //counter2.via(sharedKillSwitch.flow).runWith(Sink.ignore)


  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown()
  }

  // MergeHub

  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()
  // Source(1 to 10).runWith(materializedSink)
  // Any flow can be plugged into the same materialized sink

  // BroadcastHub

  val dynamicBroadcast = BroadcastHub.sink[Int]

  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)

  //materializedSource.runWith(Sink.ignore)
  //materializedSource.runWith(Sink.foreach[Int](println))

  // EX, combine a merge hub and a broadcast hub
  // pub-sub component
  val (pubPort, subPort) = MergeHub.source[String].toMat(BroadcastHub.sink[String])(Keep.both).run()

  subPort.runWith(Sink.foreach(e => println(s"got $e")))
  subPort.map(_.length).runWith(Sink.foreach(e => println(s"got length $e")))

  Source(List("dogs", "cats")).runWith(pubPort)
  Source.single("Streams").runWith(pubPort)

}
