package akkaessentials.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActorsExercise extends App {

  // distributed word counting

  object WordCounterMaster {
    case class Initialize(nChildren: Int)
    case class WordCountTask(text: String, taskId: Int)
    case class WordCountReply(count: Int, taskId: Int)
  }

  class WordCounterMaster extends Actor {
    import WordCounterMaster._
    override def receive: Receive = {
      case Initialize(n) =>
        val children = (0 until n).toList.map(_ => context.actorOf(Props[WordCounterWorker]))
        context.become(receiveWithChildren(children))
    }

    def receiveWithChildren(
      children: List[ActorRef],
      counter: Int = 0,
      taskId: Int = 0,
      requestMap: Map[Int, ActorRef] = Map.empty[Int, ActorRef]
    ): Receive = {
      case text: String =>
        val originalSender = sender()
        val child = children(counter)
        child ! WordCountTask(text, taskId)
        context.become(receiveWithChildren(children, (counter + 1)  % children.length, taskId + 1, requestMap + (taskId -> originalSender)))
      case WordCountReply(count, idx) =>
        requestMap(idx) ! WordCountReply(count, idx)
        context.become(receiveWithChildren(children, counter, taskId, requestMap - idx))
    }

  }

  class WordCounterWorker extends Actor {
    import WordCounterMaster._
    override def receive: Receive = {
      case WordCountTask(text, oi) => sender() ! WordCountReply(text.split(" ").length, oi)
    }
  }

  import WordCounterMaster._

  class Main extends Actor {
    override def receive: Receive = {
      case "start" =>
        val parent = context.actorOf(Props[WordCounterMaster])
        parent ! Initialize(3)
        parent ! "hi there"
        parent ! "dogs rule"
      case WordCountReply(count, _) => println(f"Got reply back $count")
    }
  }


  val system = ActorSystem("parentChildEx")

  val main = system.actorOf(Props[Main])
  main ! "start"



  /*
  create master, send Initialize(10)
  send some text to master, send it WordCountTask to one of the children
  child replies WordCountReply, master sends the response to sender

  using round robin
   */

}
