package streams.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {

  implicit val system = ActorSystem("first-principles")
  implicit val materializer = ActorMaterializer()

  // source

  val source = Source(1 to 10)

  // sink
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)

  graph.run()

  // flows transform elements

  val flow = Flow[Int].map(x => x + 1)

  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

  sourceWithFlow.to(sink).run()

  // nulls are not allowed!

  //val illegalSource = Source.single[String](null)

  // exception illegalSource.to(Sink.foreach(println)).run(), use option!

  val finiteSource = Source.single(1)

  val anotherFiniteSource = Source(List(1, 2, 3))

  val emptySource = Source.empty[Int]

  val infiniteSource = Source(Stream.from(1))

  import scala.concurrent.ExecutionContext.Implicits.global

  val futureSource = Source.fromFuture(Future(42))

  // Sinks

  val boringSink = Sink.ignore

  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // consumes first element and closes

  val foldSink = Sink.fold[Int, Int](0)(_ + _)

  // flows

  val mapFlow = Flow[Int].map(_ + 2)
  val takeFlow = Flow[Int].take(4)
  // drop, filter, DO NOT have flat map!

  // source -> flow -> flow -> sink

  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  doubleFlowGraph.run()

  // syntactic sugar

  val mapSource = Source(1 to 10).map(_ + 2) // same as source via flow map
 // mapSource.runForeach(println) // same as source to Sink.foreach ...

  // Operators or components

  // EX: create a stream that takes the names of persons, keep first two names with length > 5

  val nameSource = Source(List("alison", "bob", "jackson", "bim"))
  nameSource.filter(_.length > 5).take(3).runForeach(println)

}
