package streams.techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration._
object IntegratingWithActors extends App {

  implicit val system = ActorSystem("integrating-with-actors")
  implicit val materializer = ActorMaterializer()

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(f"got string $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(f"got number $n")
        sender() ! 2 * n
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor])

  val numberSource = Source(1 to 10)

  implicit val timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

  // numberSource.via(actorBasedFlow).to(Sink.ignore).run()
  // numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run()

  // Actor as a source

  val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)

  val materializedActorRef = actorPoweredSource.to(Sink.foreach[Int](x => println(f"Actor powered $x"))).run()

  materializedActorRef ! 10

  materializedActorRef ! akka.actor.Status.Success("Completed")

  // Actor as a destination for messages
  // support init message, an ack message, complete message, function to generate message in case stream throws

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream init")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)
      case StreamFail(e) =>
        log.warning(f"Failed $e")
      case message =>
        log.info(f"Message $message has landed")
        sender() ! StreamAck
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor])

  val actorPoweredSink = Sink.actorRefWithAck[Int](
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    onFailureMessage = e => StreamFail(e)
  )

  Source(1 to 10).to(actorPoweredSink).run()

  // Sink.actorRef not really used because it does not provide backpressure
}
