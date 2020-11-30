package persistence.stores

import akka.actor.{ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object LocalStores extends App {

  val system = ActorSystem("local-stores", ConfigFactory.load().getConfig("localStores"))

  val actor = system.actorOf(Props[SimpleActor], "simple")

  (1 to 10).foreach(i => actor ! f"akka $i")
  actor ! "print"
  actor ! "snap"
  (11 to 20).foreach(i => actor ! f"akka $i")

}
