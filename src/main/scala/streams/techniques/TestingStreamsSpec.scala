package streams.techniques

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import akka.pattern.pipe
import akka.stream.testkit.scaladsl.{TestSink, TestSource}

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success}

class TestingStreamsSpec extends TestKit(ActorSystem("akka-steams"))
  with WordSpecLike
  with BeforeAndAfterAll {

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A simple stream" should {
    "satisfy basic assertions" in {

      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)

      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)
    }

    "integrate with test actors" in {
      import system.dispatcher
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)

      val probe = TestProbe()
      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)
      probe.expectMsg(55)
    }

    "integrate with a test actor based sink" in {
      val simpleSource = Source(1 to 5)
      val flow = Flow[Int].scan[Int](0)(_ + _) // 0, 1, 3, 6, 10, 15
      val streamUnderTest = simpleSource.via(flow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "complete")
      streamUnderTest.to(probeSink).run()

      probe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
    }

    "integrate with streams test kit Sink" in {
      val sourceUnderTest = Source(1 to 5).map(_ * 2)

      val testSink = TestSink.probe[Int]
      val mat = sourceUnderTest.runWith(testSink)
      mat
        .request(5)
        .expectNext(2, 4, 6, 8, 10)
        .expectComplete()
    }

    "integrate with test kit Source" in {
      import system.dispatcher
      val sinkUnderTest = Sink.foreach[Int] {
        case 13 => throw new RuntimeException("bad luck")
        case _ =>
      }

      val testSource = TestSource.probe[Int]
      val (testPublisher, resultFuture) = testSource.toMat(sinkUnderTest)(Keep.both).run()
      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()
      resultFuture.onComplete{
        case Success(_) => fail("should have failed")
        case Failure(_) =>
      }
    }
    "test flows with test source and sink" in {
      val flowUnderTest = Flow[Int].map(_ * 2)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val (publisher, subscriber) = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      publisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(10)
        .sendNext(15)
        .sendComplete()

      subscriber.request(4).expectNext(2, 10, 20, 30).expectComplete()
    }
  }

}
