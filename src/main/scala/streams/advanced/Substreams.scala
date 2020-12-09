package streams.advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

object Substreams extends App {

  implicit val system = ActorSystem("substreams")
  implicit val materializer = ActorMaterializer()

  // grouping a stream

  val wordSource = Source(List("akka", "learning", "amazing", "substreams"))

  val groups = wordSource.groupBy(30, word => if (word.isEmpty) '\0' else word.toLowerCase.charAt(0))
  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"just received $word and have $newCount")
    newCount
  })).run()

  // new substreams for each group, each substream will have different consumer

  // merging substreams back

  val textSource = Source(List("akka is good", "learning is fun", "dogs are amazing", "substreams are hard"))
  val charCountFuture = textSource.groupBy(2, str => str.length % 2)
    .map(_.length)
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right).run()

  import system.dispatcher
  charCountFuture.onComplete(println)

  // splitting a stream

  val text = "akka is good\n" +
  "learning is fun\n" +
  "dogs are amazing\n" +
  "substreams are hard\n"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(c => c == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right).run()

    anotherCharCountFuture.onComplete(println)

  // 4 flattening
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach[Int](println))
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach[Int](println))
}
