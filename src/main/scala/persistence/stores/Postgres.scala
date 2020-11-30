package persistence.stores

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object Postgres extends App {

  val system = ActorSystem("postgres-system", ConfigFactory.load().getConfig("postgresDemo"))

  val actor = system.actorOf(Props[SimpleActor], "simple")

  (1 to 10).foreach(i => actor ! f"akka $i")
  actor ! "print"
  actor ! "snap"
  (11 to 20).foreach(i => actor ! f"akka $i")

}
