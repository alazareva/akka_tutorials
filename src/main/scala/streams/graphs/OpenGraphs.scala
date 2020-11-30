package streams.graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source}

object OpenGraphs extends App {

  implicit val system = ActorSystem("open-graphs")
  implicit val materializer = ActorMaterializer()

  // create composite source that concats 2 sources

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 52)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val concat = builder.add(Concat[Int](2))
      firstSource ~> concat
      secondSource ~> concat

      SourceShape(concat.out)
    }
  )

  val sink1 = Sink.foreach[Int](x => println(s"Sink 1 $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink 2 $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  // challenge create complex flow, compose two other flows

  val flow1 = Flow[Int].map(_ + 1)
  val flow2 = Flow[Int].map(_ * 2)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      // everything operates on shapes
      val flow1Shape = builder.add(flow1)
      val flow2Shape = builder.add(flow2)

      flow1Shape ~> flow2Shape
      FlowShape(flow1Shape.in, flow2Shape.out)
    }
  )

  sourceGraph.via(flowGraph).to(sinkGraph).run()

  // flow from sink to source

  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] = {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      // everything operates on shapes
      val sourceShape = builder.add(source)
      val sinkShape = builder.add(sink)

      FlowShape(sinkShape.in, sourceShape.out)
    }
  }
}
