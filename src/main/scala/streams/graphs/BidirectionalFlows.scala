package streams.graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlows extends App {

  implicit val system = ActorSystem("bi-flows")
  implicit val materializer = ActorMaterializer()

  /*
  Example: crypto

   */

  def encrypt(n: Int)(string: String): String = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String): String = string.map(c => (c - n).toChar)

  // bidirectional flow

  val bideCryptoStaticGraph = GraphDSL.create() {
    implicit builder =>
      val encryptionFlow = builder.add(Flow[String].map(encrypt(3)))
      val decryptionFlow = builder.add(Flow[String].map(decrypt(3)))

      BidiShape(encryptionFlow.in, encryptionFlow.out, decryptionFlow.in, decryptionFlow.out)
  }

  val strings = List("akka", "is", "awesome")
  val source = Source(strings)
  val encryptedSource = Source(strings.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val unencryptedSoourceShape = builder.add(source)
      val encryptedSoourceShape = builder.add(encryptedSource)
      val bidi = builder.add(bideCryptoStaticGraph)
      val encSink = builder.add(Sink.foreach[String](e => println(s"Enc $e")))
      val decSink = builder.add(Sink.foreach[String](e => println(s"Dec $e")))

      unencryptedSoourceShape ~> bidi.in1 ; bidi.out1 ~> encSink
      decSink <~ bidi.out2 ; bidi.in2 <~ encryptedSoourceShape

      ClosedShape
    }
  )

  cryptoBidiGraph.run()

}
