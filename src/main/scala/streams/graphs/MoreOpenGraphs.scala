package streams.graphs

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape2, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

object MoreOpenGraphs extends App {

  implicit val system = ActorSystem("more-open-graphs")
  implicit val materializer = ActorMaterializer()

  // example Max 3 operator, 3 inputs, push out the max of 3

  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    max1.out ~> max2.in0

    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source(1 to 10).map(_ => 5)
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(f"Max is $x"))


  val max3RunnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val max3Shape = builder.add(max3StaticGraph)

    source1 ~> max3Shape.in(0)
    source2 ~> max3Shape.in(1)
    source3 ~> max3Shape.in(2)

    max3Shape.out ~> maxSink

    ClosedShape
  })

  // max3RunnableGraph.run()

  // non uniform out shape
  // app has transactions stream, out 1 does not modify transaction, out 2 has ids of suspicious transaction

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactions = Source(List(
    Transaction("1", "bob", "jim", 200, new Date),
    Transaction("2", "bob", "jim", 11000, new Date),
    Transaction("3", "bob", "jim", 7000, new Date)
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysis = Sink.foreach[String](id => println(f"Bad transaction id $id"))

  val transactionGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousFilter = builder.add(Flow[Transaction].filter(_.amount > 10000))
    val transactionIdExtractor = builder.add(Flow[Transaction].map[String](_.id))

    broadcast.out(0) ~> suspiciousFilter ~> transactionIdExtractor

    new FanOutShape2(broadcast.in, broadcast.out(1), transactionIdExtractor.out)
  }

  val transactionRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val transactionShape = builder.add(transactionGraph)
      transactions ~> transactionShape.in
      transactionShape.out0 ~> bankProcessor
      transactionShape.out1 ~> suspiciousAnalysis

      ClosedShape
    }
  )

  transactionRunnableGraph.run()

}
