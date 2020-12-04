package streams.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {

  implicit val system = ActorSystem("operator-fusion")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)


  // runs on the same actor bc of operator fusion
  // simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()

  // complex flow
  val complexFlow = Flow[Int].map { i =>
    Thread.sleep(1000)

    i + 1
  }

  val complexFlow2 = Flow[Int].map { i =>
    Thread.sleep(1000)

    i * 10
  }
 // simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()

  // async boundary
  // simpleSource.via(complexFlow).async.via(complexFlow2).async.to(simpleSink).run()

  // ordering guarantees

  Source(1 to 3).map(element => {println(s"Flow A: $element"); element}).async
    .map(element => {println(s"Flow B: $element"); element}).async
    .map(element => {println(s"Flow C: $element"); element}).async
    .runWith(Sink.ignore)
}
