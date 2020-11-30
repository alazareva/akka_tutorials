package streams.graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

object GraphBasics extends App {

  implicit val system = ActorSystem("graph-basics")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 10)

  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)

  val output = Sink.foreach[(Int, Int)](println)

  // step 1
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit  builder: GraphDSL.Builder[NotUsed] => // builder is a mutable data structure
      import GraphDSL.Implicits._
      // step 2 add components to graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan out
      val zip = builder.add(Zip[Int, Int]) // fan in

      // step 3 connect components
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output

      // step 4 return closed shape
      ClosedShape // freeze the builder shape and make it immutable

    } // must return a shape
  ) // Runnable graph

  // graph.run()

  // EX 1  feed a single source into 2 sinks

  val sink1 = Sink.foreach[Int](i => println(s"Sink 1: $i"))
  val sink2 = Sink.foreach[Int](i => println(s"Sink 2: $i"))

  val graph2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit  builder: GraphDSL.Builder[NotUsed] => // builder is a mutable data structure
      import GraphDSL.Implicits._
      // step 2 add components to graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan out

      // step 3 connect components
      input ~> broadcast ~> sink1
               broadcast ~> sink2

      // step 4 return closed shape
      ClosedShape // freeze the builder shape and make it immutable

    } // must return a shape
  ) // Runnable graph

  // graph2.run()

  // Ex 2 merge sources

  import scala.concurrent.duration._

  val fastSource = Source(1 to 10)
  val slowSource = Source(200 to 210).throttle(2, 1 second)


  val graph3 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit  builder: GraphDSL.Builder[NotUsed] => // builder is a mutable data structure
      import GraphDSL.Implicits._
      // step 2 add components to graph
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      // step 3 connect components
      fastSource ~> merge
      slowSource ~> merge
      merge ~> balance ~> sink1
               balance ~> sink2

      // step 4 return closed shape
      ClosedShape // freeze the builder shape and make it immutable

    } // must return a shape
  ) // Runnable graph

  graph3.run()

}
