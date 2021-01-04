package clustering.remoting

import akka.actor.{Actor, ActorLogging}

class SimpleActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case message => log.info(s"Got $message from ${sender()}")
  }

}
