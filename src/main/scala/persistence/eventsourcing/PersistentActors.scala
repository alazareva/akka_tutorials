package persistence.eventsourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActors extends App {

  case class Invoice(to: String, date: Date, amount: Int) // Command
  case class InvoiceRecorded(id: Int, to: String, date: Date, amount: Int) // Event

  case class InvoiceBulk(invoices: List[Invoice])

  case object Shutdown

  class Accountant extends PersistentActor with ActorLogging {

    var lastId = 0
    var totalAmount = 0

    override def persistenceId: String = "simple-accountant"

    override def receiveCommand: Receive = {
      case Invoice(to, date, amount) =>
        /*
          When you get a command
          create EVENT to persist to store
          persist event
          pass in callback that gets triggered when event is written
          update the actor state when event persisted
         */
        log.info(f"got invoice for $amount")
        val event = InvoiceRecorded(lastId, to, date, amount)
        persist(event) /* time gap  all other messages are stashed */ { e =>
          // here it's ok to access mutable state in callbacks
          // you can even use sender() even though it's async
          lastId += 1
          totalAmount += amount
          log.info(f"Persisted $e as ${e.id} to total $totalAmount")
        }
      // you don't have to persist all events
      case InvoiceBulk(invoices) =>
        val invoiceIds = lastId to (lastId + invoices.size)
        val events = invoices.zip(invoiceIds).map { case (invoice, id) =>
          InvoiceRecorded(id, invoice.to, invoice.date, invoice.amount)
        }
        persistAll(events) { e => // called after each event
          lastId += 1
          totalAmount += e.amount
          log.info(f"Persisted $e as ${e.id} to total $totalAmount")
        }
      case Shutdown => context.stop(self)
    }

    // on recovery
    override def receiveRecover: Receive = {
      // best practice, follow logic in receiveCommand
      case InvoiceRecorded(id, _, _, amount) =>
        lastId = id
        totalAmount += amount
        log.info(f"recovered $id, $amount, $totalAmount")
    }

    // called in persisting failed, actor will be stopped
    // best practice use backoff supervisor to restart
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(f"failed $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    // actor is resumed
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(f"rejected $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("persistent-actors")

  val accountant = system.actorOf(Props[Accountant], "simple-accountant")
  (1 to 10).foreach(i => ()
    // accountant ! Invoice("company", new Date, i + 1000)
  )

  val invoices = (1 to 5).map(i => Invoice("acme", new Date, 10 + i))
  // accountant ! InvoiceBulk(invoices.toList)

  // NEVER CALL PERSIST OR PERSIST ALL FROM FUTURES!!!

  // Shutdown of persistent actors
  // best practice is define your own and handle it context.stop(self)

}
