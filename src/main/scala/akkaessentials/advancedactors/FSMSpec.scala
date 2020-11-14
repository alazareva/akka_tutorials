package akkaessentials.advancedactors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, OneInstancePerTest, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object FSMSpec {

  /*
  Vending machine
   */

  case class Initialize(inventory: Map[String, Int], prices: Map[String, Int])

  case class RequestProduct(product: String)

  case class Instruction(instruction: String) // screen instruction
  case class ReceiveMoney(amount: Int)

  case class Deliver(product: String)

  case class GiveBackChange(amount: Int)

  case class VendingError(reason: String)

  case object ReceiveMoneyTimeout

  class VendingMachine extends Actor with ActorLogging {

    implicit val ec: ExecutionContext = context.dispatcher

    override def receive: Receive = idle

    def idle: Receive = {
      case Initialize(inventory, prices) => context.become(operational(inventory, prices))
      case _ => sender() ! VendingError("Not Init")
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) =>
        inventory.get(product) match {
          case None | Some(0) => sender() ! VendingError("Product not available")
          case Some(v) =>
            val price = prices(product)
            sender() ! Instruction(f"Insert $price")
            context.become(waitForMoney(
              inventory,
              prices,
              product,
              0,
              startReceiveMoneyTimeout(),
              sender(),
            ))
        }
    }

    def waitForMoney(
      inventory: Map[String, Int],
      prices: Map[String, Int],
      product: String,
      money: Int,
      moneyTimeoutSchedule: Cancellable,
      requester: ActorRef
    ): Receive = {
      case ReceiveMoneyTimeout =>
        requester ! VendingError("Timed out")
        if (money > 0) requester ! GiveBackChange(money)
        context.become(operational(inventory, prices))
      case ReceiveMoney(amount) =>
        moneyTimeoutSchedule.cancel()
        val price = prices(product)
        if (money + amount >= price) {
          requester ! Deliver(product)
          if (money + amount > price) {
            requester ! GiveBackChange(money + amount - price)
          }
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          context.become(operational(newInventory, prices))
        } else {
          val remaining = price - money - amount
          requester ! Instruction(f"Insert $remaining")
          context.become(waitForMoney(
            inventory,
            prices,
            product,
            money + amount,
            startReceiveMoneyTimeout(),
            requester,
          ))
        }
    }

    def startReceiveMoneyTimeout(): Cancellable = context.system.scheduler.scheduleOnce(1 second) {
      self ! ReceiveMoneyTimeout
    }
  }

  // Using FSM
  // state, data, Event => state and data can be changed
  trait VendingState

  case object Idle extends VendingState

  case object Operational extends VendingState

  case object WaitForMoney extends VendingState

  trait VendingData

  case object Uninitialized extends VendingData

  case class Initialized(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData

  case class WaitForMoneyData(
    inventory: Map[String, Int],
    prices: Map[String, Int],
    product: String,
    money: Int,
    requester: ActorRef
  ) extends VendingData

  class VendingMachineFSM extends FSM[VendingState, VendingData] { // is an Actor
    // don't handle messages directly, messages trigger an Event(message, data)
    startWith(Idle, Uninitialized)
    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        goto(Operational) using Initialized(inventory, prices)
      case _ =>
        sender() ! VendingError("Not Init")
        stay()
    }
    when(Operational) {
      case Event(RequestProduct(product), Initialized(inventory, prices)) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError("Product not available")
            stay()
          case Some(_) =>
            val price = prices(product)
            sender() ! Instruction(f"Insert $price")
            goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, 0, sender())
        }

    }
    when(WaitForMoney, stateTimeout = 1 second) {
      case Event(StateTimeout, WaitForMoneyData(inventory, prices, product, money, requester)) =>
        requester ! VendingError("Timed out")
        if (money > 0) requester ! GiveBackChange(money)
        goto(Operational) using Initialized(inventory, prices)
      case Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, money, requester)) =>
        val price = prices(product)
        if (money + amount >= price) {
          requester ! Deliver(product)
          if (money + amount > price) {
            requester ! GiveBackChange(money + amount - price)
          }
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          goto(Operational) using Initialized(newInventory, prices)
        } else {
          val remaining = price - money - amount
          requester ! Instruction(f"Insert $remaining")
          stay() using WaitForMoneyData(inventory, prices, product, money + amount, requester)
        }
    }

    whenUnhandled {
      case Event(_, _) =>
        sender() ! VendingError("Command not found")
        stay()
    }

    onTransition {
      case stateA -> stateB => log.info(f"Transition $stateA -> $stateB")
    }

    initialize()
  }

}

class FSMSpec extends
  TestKit(ActorSystem("fsmSpec")) with
  ImplicitSender with
  WordSpecLike with
  BeforeAndAfterAll with
  OneInstancePerTest {

  import FSMSpec._

  def runTestSuite(props: Props): Unit = {
    "error when not initialized" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! RequestProduct("foo")
      expectMsg(VendingError("Not Init"))
    }
    "report product not available" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 19), Map("coke" -> 1))
      vendingMachine ! RequestProduct("foo")
      expectMsg(VendingError("Product not available"))
    }
    "throw timeout if don't insert money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 19), Map("coke" -> 1))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(f"Insert 1"))
      within(1.5 seconds) {
        expectMsg(VendingError("Timed out"))
      }
    }
    "handle reception of money " in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 19), Map("coke" -> 5))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(f"Insert 5"))
      vendingMachine ! ReceiveMoney(1)
      expectMsg(Instruction(f"Insert 4"))
      within(1.5 seconds) {
        expectMsg(VendingError("Timed out"))
        expectMsg(GiveBackChange(1))
      }
    }

    "deliver product " in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 19), Map("coke" -> 5))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(f"Insert 5"))
      vendingMachine ! ReceiveMoney(5)
      expectMsg(Deliver("coke"))
    }

    "give change product and go back to operation" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 19), Map("coke" -> 5))
      vendingMachine ! RequestProduct("coke")

      expectMsg(Instruction(f"Insert 5"))
      vendingMachine ! ReceiveMoney(6)
      expectMsg(Deliver("coke"))
      expectMsg(GiveBackChange(1))

      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction(f"Insert 5"))
    }
  }

  "a vending machine" should {
    runTestSuite(Props[VendingMachine])
  }

  "a vending machine fsm" should {
    runTestSuite(Props[VendingMachineFSM])
  }

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

}
