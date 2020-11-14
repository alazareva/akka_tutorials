package persistence.eventsourcing

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object MultiplePersists extends App {

  // diligent accountant, with every invoice will persist 2 events, Tax record and Invoice

  case class Invoice(to: String, date: Date, amount: Int)

  // Events
  case class TaxRecord(taxId: String, recordId: Int, date: Date, amount: Int)
  case class InvoiceRecord(invoiceId: Int, to: String, date: Date, amount: Int)

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef) = Props(new DiligentAccountant(taxId, taxAuthority))
  }

  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {

    var latestTaxId = 0
    var invoiceId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(to, date, amount) => // message ordering is guaranteed
        persist(TaxRecord(taxId, latestTaxId, date, amount / 3)) { e =>
          taxAuthority ! e
          latestTaxId += 1
          persist("Certified tax") {e =>
            taxAuthority ! e
          }
        }
        persist(InvoiceRecord(invoiceId, to, date, amount)) { e =>
          taxAuthority ! e
          invoiceId += 1
          persist("Certified invoice") {e =>
            taxAuthority ! e
          }
        }
    }

    override def receiveRecover: Receive = {
      case event => log.info(f"recovered $event")
    }
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"$message")
    }
  }

  val system = ActorSystem("multiple-persist")

  val taxAuthority = system.actorOf(Props[TaxAuthority], "hmrc")
  val accountant = system.actorOf(DiligentAccountant.props("uk11", taxAuthority))

  accountant ! Invoice("co", new Date, 2000)

  // nested persisting


}
