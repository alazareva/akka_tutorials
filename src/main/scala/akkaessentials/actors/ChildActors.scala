package akkaessentials.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActors extends App {

  // actors can create other actors

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }

  class Parent extends Actor {
    import Parent._
    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child")
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
      }

    def withChild(child: ActorRef): Receive = {
      case TellChild(message) => child forward message

    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"${self.path} I got $message")
    }
  }
  import Parent._

  val system = ActorSystem("parentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("Hi")

  // There are 3 guardian actors
  // - /system = system guardian
  // - /user = user level guardian, all actors we create are owned by user
  // - / root guardian, manages system and user

  // how to find actor by path, actor selection

  val childSelection = system.actorSelection("/user/parent/child")
  childSelection ! "found" // if invalid path messages will be dropped

  // DANGER: never pass mutable actor state or 'this' reference to child actors!
  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitAccount

  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._
    var balance = 0
    override def receive: Receive = {
      case InitAccount =>
        val creditCard = context.actorOf(Props[CreditCard], "card")
        creditCard ! AttachToAccount(this)
      case Deposit(a) => deposit(a)
      case Withdraw(a) => withdraw(a)
    }

    def deposit(a: Int) = {
      println(f"depositing $a on top of $balance")
      balance += a
    }
    def withdraw(a: Int) = {
      println(f"withdrawing $a from $balance")
      balance -= a
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) // not good, needs to be ActorRef
    case object CheckStatus
  }

  class CreditCard extends Actor {
    import NaiveBankAccount._
    import CreditCard._
    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachedTo(account))
    }
    def attachedTo(acc: NaiveBankAccount): Receive = {
      case CheckStatus => println(s"${self.path} processed")
        acc.withdraw(1) // WRONG
    }
  }
  import NaiveBankAccount._
  import CreditCard._

  val bankRef = system.actorOf(Props[NaiveBankAccount], "account")
  bankRef ! InitAccount
  bankRef ! Deposit(100)

  Thread.sleep(100)
  val ccSelection = system.actorSelection("user/account/card")
  ccSelection ! CheckStatus
}
