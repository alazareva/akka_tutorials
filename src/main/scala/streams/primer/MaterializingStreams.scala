package streams.primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

object MaterializingStreams extends App {

  implicit val system = ActorSystem("materializing-streams")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))

  // val simpleMaterializedValue = simpleGraph.run() // NotUsed

  val source = Source(1 to 10)

  val sink = Sink.reduce[Int](_ + _)
  val sumFuture = source.runWith(sink)
  sumFuture.onComplete {
    case Success(value) => println(s"success $value")
    case Failure(exception) => println(s"failed $exception")
  }

  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleSink = Sink.foreach[Int](println)
  val runnableGraph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)

  runnableGraph.run().onComplete {
    case Success(_) => println("Done")
    case Failure(exception) => println(s"failed $exception")
  }

  // sugars

  Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // or
  Source(1 to 10).runReduce(_ + _)


  Sink.foreach[Int](println).runWith(Source.single(42))

  Flow[Int].map(_ + 1).runWith(simpleSource, simpleSink)

  // EX: return the last element out of a source Sink.last
  // total word count out of string of sentences, map fold reduce

  // 1

  val res1 = Source(1 to 5).toMat(Sink.last[Int])(Keep.right).run()
  val res2 = Source(1 to 5).runWith(Sink.last[Int])

  // 2

  val totalWordCount = Source(List("dogs rule", "i love dogs")).map(_.split(" ").length).runReduce(_ + _)
  val totalWordCountSink = Sink.fold[Int, String](0)(
    (currentWords, newSentence) => currentWords + newSentence.split(" ").length
  )
  val wordCount2 =  Source(List("dogs rule", "i love dogs")).runWith(totalWordCountSink)

}
