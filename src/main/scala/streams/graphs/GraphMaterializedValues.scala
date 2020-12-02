package streams.graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {

  implicit val system = ActorSystem("mat-values")
  implicit val materializer = ActorMaterializer()

  val wordSource = Source(List("dog", "cats", "Scala", "Akka"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
  - A composite component
    prints all strings that are lowercase
     - counts strings that are short < 5 chars

   */

  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(printer, counter)((printerMat, counterMat) => counterMat) {
      implicit builder => (printerShape, counterShape) =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortFiler = builder.add(Flow[String].filter(_.length < 5))

      broadcast ~> lowercaseFilter ~> printerShape
      broadcast ~> shortFiler ~> counterShape

      SinkShape(broadcast.in)
    }
  )
  val futureInt = wordSource.toMat(complexWordSink)(Keep.right).run()

  import system.dispatcher

  futureInt.onComplete{
    case Success(count) => println(f"Total number $count")
    case Failure(_) => println(f"Failed")
  }

  /*
  write method
   */

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val foldSink = Sink.fold[Int, B](0)((count, _) => count + 1)
    Flow.fromGraph( GraphDSL.create(foldSink, flow)((sinkMat, _) => sinkMat) {
      implicit builder => (sinkShape, flowShape) =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[B](2))
      flowShape ~> broadcast
                   broadcast.out(0) ~> sinkShape

        FlowShape(flowShape.in, broadcast.out(1))
      }
    )
  }

  val enhancedFlow = enhanceFlow(Flow[Int].map(_ + 1))

  val futureCount = Source(1 to 10).viaMat(enhancedFlow)(Keep.right).toMat(Sink.ignore)(Keep.left).run()

  futureCount.onComplete{
    case Success(count) => println(f"Total int count $count")
    case Failure(_) => println(f"Failed")
  }

}
