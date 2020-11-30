package persistence.advanced

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object EventAdapters extends App {

  val acoustic = "acoustic"
  val electric = "electric"
  // store for guitars

  case class Guitar(id: String, model: String, make: String, guitarType: String = acoustic)
  case class AddGuitar(guitar: Guitar, quantity: Int)

  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)
  case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)

  class InventoryManager extends PersistentActor with ActorLogging {

    val inventory = new mutable.HashMap[Guitar, Int]()

    override def persistenceId: String = "guitar-manager"

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, quantity, guitarType)) { e =>
          addGuitar(guitar, quantity)
          log.info(f"Added $quantity to $guitar")
        }
      case "print" => log.info(s"Current inventory iss $inventory")
    }

    override def receiveRecover: Receive = {
      case event @ GuitarAddedV2(id, model, make, quantity, guitarType) =>
        log.info(s"recovered $event")
        val guitar = Guitar(id, model, make, guitarType)
        addGuitar(guitar, quantity)
    }

    def addGuitar(guitar: Guitar, quantity: Int): Unit = {
      val existingQuantity = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existingQuantity + quantity)
    }
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
    /*
    journal -> serializer -> event adapter -> actor
     */

    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(guitarId, guitarModel, guitarMake, quantity) =>
        EventSeq.single(GuitarAddedV2(guitarId, guitarModel, guitarMake, quantity, acoustic))
      case other => EventSeq.single(other)
    }
  }

  // add GuitarReadEventAdapter to config

  val system = ActorSystem("eventAdapters", ConfigFactory.load().getConfig("eventAdapters"))

  val manager = system.actorOf(Props[InventoryManager], "manager")

  manager ! "print"
 //  (0 to 10).foreach(i => manager ! AddGuitar(Guitar(i.toString, "hacker", "trjvm"), 5))

  // write event adapter actor -> adapter -> seriialzer -> journal
  // used for backwards compatibility, has toJournal

  // EventAdapter

}
