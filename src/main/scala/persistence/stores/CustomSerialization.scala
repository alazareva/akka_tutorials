package persistence.stores

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory


case class RegisterUser(email: String, name: String)
case class UserRegistered(id: Int, email: String, name: String)

class UserRegistrationSerializer extends Serializer {

  override def identifier: Int = 12234

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e @ UserRegistered(id, email, name) =>
      println(f"serializing $e")
      f"[$id//$email//$name]".getBytes
    case _ => throw new IllegalArgumentException("unsupported")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split("//")

    val id = values(0).toInt
    val email = values(1)
    val name = values(2)
    val result = UserRegistered(id, email, name)
    println(s"deserialized $string to $result")
    result
  }

  override def includeManifest: Boolean = false

}

class UserRegistrationActor extends PersistentActor with ActorLogging {

  var currentId = 0
  override def persistenceId: String = "user-registration"

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { e =>
        currentId += 1
        log.info(f"persisted $e")
      }
  }

  override def receiveRecover: Receive = {
    case UserRegistered(id, _, _) =>
      log.info(f"recovered $id")
      currentId = id
  }
}

object CustomSerialization extends App {

  // send command to actor
  // actor calls persist
  // serializer serializes event
  // journal writes bytes

  val system = ActorSystem("serialization-demo", ConfigFactory.load().getConfig("customSerializerDemo"))

  val actor = system.actorOf(Props[UserRegistrationActor])

  // (0 to 10).foreach(i => actor ! RegisterUser(f"user_$i@gmail.com", f"user_$i"))

}
