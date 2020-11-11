package akkaessentials.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {

  val system = ActorSystem("mailboxes", ConfigFactory.load().getConfig("mailboxDemo"))

  class SimpleActor extends Actor with ActorLogging {

    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  // Custom priority mailbox
  // P0 -> most important etc
  // define mailbox
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _ => 4
      })

  // make it known in config
  // attach dispatcher to actor

  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))

  supportTicketLogger ! PoisonPill
  supportTicketLogger ! "[P3] nice to have"
  supportTicketLogger ! "[P0] important"
  supportTicketLogger ! "[P1] not important"

  // after how much time do you have to wait to send a new message?
  // there is no way to find out or configure

  // Interesting case - control aware mailbox
  // will use existing class

  // step 1 mark message as control messages

  case object Ticket extends ControlMessage

  // step 2 configure who gets mailbox, make the actor attach to mailbox

  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))

  controlAwareActor ! "[P3] nice to have"
  controlAwareActor ! "[P0] important"
  controlAwareActor ! Ticket


  // method 2 - using deployment config

  val controlAwareActor2 = system.actorOf(Props[SimpleActor], "controlAwareActor2")

  controlAwareActor2 ! "[P3] nice to have"
  controlAwareActor2 ! "[P0] important"
  controlAwareActor2 ! Ticket

}
