package streams.advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Graph, Inlet, Outlet, Shape}

import scala.concurrent.duration._
import scala.collection.immutable

object CustomGraphShapes extends App {

  implicit val system = ActorSystem("custom-shapes")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

 case class Balance2x3(
   in0: Inlet[Int],
   in1: Inlet[Int],
   out0: Outlet[Int],
   out1: Outlet[Int],
   out2: Outlet[Int],
  ) extends Shape {

   override val inlets: immutable.Seq[Inlet[_]] = List(in0, in1)
   override val outlets: immutable.Seq[Outlet[_]] = List(out0, out1, out2)

   override def deepCopy(): Shape = Balance2x3(
     in0.carbonCopy(),
     in1.carbonCopy(),
     out0.carbonCopy(),
     out1.carbonCopy(),
     out2.carbonCopy()
   )
  }


  val balanced2x3Impl = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits._
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](3))

      merge ~> balance

      Balance2x3(
        merge.in(0),
        merge.in(1),
        balance.out(0),
        balance.out(1),
        balance.out(2)
      )
  }

  val balanced2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._
        val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
        val fastSource = Source(Stream.from(2)).throttle(1, 1 second)

        def createSink(index: Int) = Sink.fold(0)((count: Int, elem: Int) => {
          println(s"Sink $index got $elem, current count $count")
          count + 1
        })

        val sink1 = builder.add(createSink(1))
        val sink2 = builder.add(createSink(2))
        val sink3 = builder.add(createSink(3))

        val balance = builder.add(balanced2x3Impl)

        slowSource ~> balance.in0
        fastSource ~> balance.in1

        balance.out0 ~> sink1
        balance.out1 ~> sink2
        balance.out2 ~> sink3
        ClosedShape
    }
  )
 //  balanced2x3Graph.run()

  // Generalize the balance component

  case class BalanceNxM[T](
    override val inlets: immutable.Seq[Inlet[T]],
    override val outlets: immutable.Seq[Outlet[T]],
  ) extends Shape {

    override def deepCopy(): Shape = BalanceNxM(
      inlets.map(_.carbonCopy()),
      outlets.map(_.carbonCopy())
    )
  }

  object BalanceNxM {
    def apply[T](inputCount: Int, outputCount: Int): Graph[BalanceNxM[T], NotUsed] =  GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._
        val merge = builder.add(Merge[T](inputCount))
        val balance = builder.add(Balance[T](outputCount))

        merge ~> balance

        BalanceNxM(merge.inlets.toList, balance.outlets.toList)
    }
  }

  val balanced2x3GraphV2 = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._
        val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
        val fastSource = Source(Stream.from(2)).throttle(1, 1 second)

        def createSink(index: Int) = Sink.fold(0)((count: Int, elem: Int) => {
          println(s"Sink $index got $elem, current count $count")
          count + 1
        })

        val sink1 = builder.add(createSink(1))
        val sink2 = builder.add(createSink(2))
        val sink3 = builder.add(createSink(3))

        val balance = builder.add(BalanceNxM[Int](2, 3))

        slowSource ~> balance.inlets(0)
        fastSource ~> balance.inlets(1)

        balance.outlets(0) ~> sink1
        balance.outlets(1) ~> sink2
        balance.outlets(2) ~> sink3
        ClosedShape
    }
  )

  balanced2x3GraphV2.run()
}
