package akkaessentials.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorsIntro extends App {

  // actor systems

  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  // create actors
  // word count actor

  class WordCountActor extends Actor {
    var totalWords = 0

    override def receive: PartialFunction[Any, Unit] = {
      case message: String =>
        println(s"I got message $message")
        totalWords += message.split(" ").length
      case msg => println(s"I don't get $msg")
    }
  }

  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  val wordCounter2 = actorSystem.actorOf(Props[WordCountActor], "wordCounter2")


  // communicate

  wordCounter ! "learning akka"
  wordCounter2 ! "learning akka again"

  class Person(name: String) extends Actor {
    override def receive: Receive = {
      case "Hi" => println(s"Hello from $name")
    }
  }

  object Person {
    def props(name: String) = Props(new Person(name))
  }

  val person = actorSystem.actorOf(Person.props("Bob"), "bob")

  person ! "Hi"

  case class SpecialMessage(content: String)

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi" => context.sender() ! "Hello there"
      case message: String => println(s"${context.self.path} I got $message")
      case number: Int => println(s"I got a number $number")
      case SpecialMessage(content) => println(s"I got a SpecialMessage $content")
      case SendMessageToYourself(content) => self ! content
      case SayHiTo(ref) => ref ! "Hi"
      case WirelessPhoneMessage(m, r) => r forward (m + "s")
    }
  }

  val simpleActor = actorSystem.actorOf(Props[SimpleActor], "simple")

  simpleActor ! "Hello actor"

  // messages can be any type
  simpleActor ! 42

  // must be serializable and immutable, in practice use case classes and objects
  simpleActor ! SpecialMessage("dogs")

  // actors have information about their context and themselves
  // context.self === 'this'

  case class SendMessageToYourself(content: String)

  simpleActor ! SendMessageToYourself("self sending")

  // actors can reply to messages

  val alice = actorSystem.actorOf(Props[SimpleActor], "alice")
  val john = actorSystem.actorOf(Props[SimpleActor], "john")

  case class SayHiTo(ref: ActorRef)

  alice ! SayHiTo(john)

  // forwarding messages
  // A -> B -> C
  // sending a message keep original sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)

  alice ! WirelessPhoneMessage("Hi", john)

  // Exercises
  // 1) create a counter actor, responds to increment, or decrement, print
  // 2) bank account as an actor: deposit , withdraw, statement replies with Success/Failure
  // make it interact with some other kind of actor and print reply

  case object Counter {
    case object Inc
    case object Dec
    case object Print

  }

  class Counter extends Actor {
    import Counter._
    var c = 0

    override def receive: Receive = {
      case Inc => c += 1
      case Dec => c -= 1
      case Print => println(f"[counter] Count is $c")
    }
  }
  println("Counter Example")

  val counter = actorSystem.actorOf(Props[Counter], "counter")

  counter ! Counter.Inc
  counter ! Counter.Inc
  counter ! Counter.Dec
  counter ! Counter.Print


  trait BankResponse
  case object Failed extends BankResponse
  case object OK extends BankResponse

  trait Transaction
  case class Deposit(amount: Int, ref: ActorRef) extends Transaction
  case class Withdraw(amount: Int, ref: ActorRef) extends Transaction
  case class Statement(ref: ActorRef) extends Transaction

  case class StatementReport(report: String)

  class BankTeller extends Actor {

    override def receive: Receive = {
      case Deposit(a, ref) => ref ! a
      case Withdraw(a, ref) => ref ! -a
      case Statement(ref) => ref ! Statement
      case Failed => println("Failed transaction")
      case OK => println("OK")
      case StatementReport(r) => println(r)
    }

  }

  class BankAccount extends Actor {
    private var balance = 0
    override def receive: Receive = {
      case amount: Int => {
      if (balance + amount < 0) context.sender() ! Failed
      else {
        balance += amount
        context.sender() ! OK
      }
    }
      case Statement => context.sender() ! StatementReport(f"Balance is $balance")
    }
  }

  val teller = actorSystem.actorOf(Props[BankTeller], "teller")
  val bankAccount = actorSystem.actorOf(Props[BankAccount], "bankAccount")

  teller ! Deposit(1000, bankAccount)
  teller ! Withdraw(200, bankAccount)
  teller ! Statement(bankAccount)

}
