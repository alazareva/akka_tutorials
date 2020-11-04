package akkaessentials.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object IntroAkkaConfig extends App {

  class SimpleLoggingActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case message => log.info(message.toString)
    }

  }

  // using string
  val configString =
    """
      |akka {
      | loglevel = ERROR
      |}
    """.stripMargin

  /*
  val config = ConfigFactory.parseString(configString)

  val system = ActorSystem("configDemo", config)

  val actor = system.actorOf(Props[SimpleLoggingActor])

  actor ! "log me"


  // using file

  val defaultConfigSystem = ActorSystem("defaultConfig")

  val defaultLoggingActor = defaultConfigSystem.actorOf(Props[SimpleLoggingActor])

  defaultLoggingActor ! "log me 2"


  // separate configs in same file

  val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")

  val specialConfigSystem = ActorSystem("specialConfig", specialConfig)

  val specialConfigActor = specialConfigSystem.actorOf(Props[SimpleLoggingActor])

  specialConfigActor ! "hey there"

   */

  // a config in anothr file

  val secretConfig = ConfigFactory.load("secretConfig/secretConf.conf")

  println(s"log level = ${secretConfig.getString("akka.loglevel")}")

  // parsing different file formats

  val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
  println(s"json config ${jsonConfig.getString("akka.loglevel")}")

  val propertiesConfig = ConfigFactory.load("props/propsConfig.properties")
  println(s"properties config ${propertiesConfig.getString("akka.loglevel")}")


}
