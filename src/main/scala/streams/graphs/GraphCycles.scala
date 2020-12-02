package streams.graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy, UniformFanInShape}

object GraphCycles extends App {

  implicit val system = ActorSystem("cycles")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 10))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(f"Accelerating $x")
      x + 1
    })
    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock

  // Solution 1: MergePreferred

  val acceleratorMerge = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 10))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(f"Accelerating $x")
      x + 1
    })
    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(acceleratorMerge).run()

  // Solution 2: drop buffer elements

  val acceleratorBuffered = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 10))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(f"Accelerating $x")
      Thread.sleep(100)
      x
    })
    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(acceleratorBuffered).run()

  /*
  Create a fan in shape
  takes 2 inputs fed with 1 number
  output will will emit fib sequence
   */

  val fib = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val zip = builder.add(Zip[BigInt, BigInt])
    val mergeShape = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val flow = builder.add(Flow[(BigInt, BigInt)].map(
      {case (last, prev) =>
        Thread.sleep(1000)
        (last + prev, last)
      })
    )
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map({case (last, _) => last}))

    zip.out ~> mergeShape ~> flow ~> broadcast ~> extractLast
    mergeShape.preferred <~ broadcast

    UniformFanInShape(extractLast.out, zip.in0, zip.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val source1 = builder.add(Source.single[BigInt](1))
      val source2 = builder.add(Source.single[BigInt](1))
      val sink = builder.add(Sink.foreach[BigInt](x => println(f"Num $x")))
      val fibo = builder.add(fib)

      source1 ~> fibo.in(0)
      source2 ~> fibo.in(1)
      fibo.out ~> sink

      ClosedShape
    }
  )

  fiboGraph.run()

}



