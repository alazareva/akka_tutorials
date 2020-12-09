package streams.advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.Random

object CustomOperators extends App {

  implicit val system = ActorSystem("custom-operators")
  implicit val materializer = ActorMaterializer()

  // custom source that emits random numbers

  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {

    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    override def shape: SourceShape[Int] = SourceShape(outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape){
      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          // emit new element
          val nextNumber = Random.nextInt(max)
          // push it out of out port
          push(outPort, nextNumber)
        }
      })
    }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))

  // custom sink that prints elements in batches

  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {

    val inPort = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)


    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      override def preStart(): Unit = {
        pull(inPort)
      }

      // add mutable state
      val batch = new mutable.Queue[Int]()
      setHandler(inPort, new InHandler {

        // when upstream sends element
        override def onPush(): Unit = {
          // backpressure happens here
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)
          if (batch.size >= batchSize) {
            println(f"New batch ${batch.dequeueAll(_ => true).mkString("[", ", ", "]")}")
          }
          pull(inPort) // Send demand upstream
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println(f"Stream finished")
            println(f"New batch ${batch.dequeueAll(_ => true).mkString("[", ", ", "]")}")
          }
        }
      })
    }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))

  // randomGeneratorSource.runWith(batcherSink)

  // create a simple filer flow, input and output port with 2 handlers

  class FilerFlow[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {

    val inPort = Inlet[T]("inFilter")
    val outPort = Outlet[T]("outFiler")

    override def shape: FlowShape[T, T] = FlowShape[T, T](inPort, outPort)
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          try {
            val nextElement = grab(inPort)
            if (predicate(nextElement)) push(outPort, nextElement)
            else pull(inPort)
          } catch {
            case e: Throwable => failStage(e)
          }
        }})

      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          pull(inPort)
        }
      })
    }
  }

  val filterFlow = Flow.fromGraph(new FilerFlow[Int](_ % 2 == 0))
  // randomGeneratorSource.via(filterFlow).runWith(batcherSink)

  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {

    val inPort = Inlet[T]("inCounter")
    val outPort = Outlet[T]("outCounter")

    override val shape: FlowShape[T, T] = FlowShape[T, T](inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {

      val promise = Promise[Int]
      val logic = new GraphStageLogic(shape) {
        // setting mutable state
        var counter = 0

        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = pull(inPort)

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }
        })

        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            val element = grab(inPort)
            counter += 1
            push(outPort, element)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })
      }
      (logic, promise.future)
    }
  }


  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  val mat = Source(1 to 10).viaMat(counterFlow)(Keep.right).to(Sink.foreach[Int](println)).run()
  import system.dispatcher
  mat.onComplete(println)
}


