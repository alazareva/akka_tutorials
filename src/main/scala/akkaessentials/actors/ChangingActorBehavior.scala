package akkaessentials.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehavior extends App {

  object Mom {

    case class Food(food: String)

    case class Ask(message: String)

    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"

    case class MomStart(kid: ActorRef)

  }

  object FussyKid {

    case object KitAccept

    case object KitReject

    val HAPPY = "happy"
    val SAD = "sad"
  }

  class FussyKid extends Actor {

    import FussyKid._
    import Mom._

    var state = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KitAccept
        else sender() ! KitReject
    }
  }

  class StatelessFussyKid extends Actor {

    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false) // change handler to sad receive
      case Food(CHOCOLATE) => ()
      case Ask(_) => sender() ! KitAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) => context.unbecome()
      case Ask(_) => sender() ! KitReject
    }
  }

  class Mom extends Actor {

    import Mom._
    import FussyKid._

    override def receive: Receive = {
      case MomStart(kid) =>
        kid ! Food(VEGETABLE)
        kid ! Food(VEGETABLE)
        kid ! Food(CHOCOLATE)
        kid ! Food(CHOCOLATE)
        kid ! Ask("Do you want to play")
      case KitAccept => println("Yey, you are happy")
      case KitReject => println("My kid is sad")
    }
  }

  val system = ActorSystem("changingBehavior")

  val fussyKid = system.actorOf(Props[FussyKid], "kid")
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid], "statelessKid")

  val mom = system.actorOf(Props[Mom], "mom")

  mom ! Mom.MomStart(statelessFussyKid)

  // context.become(sadReceive, false) adds context to a stack
  // Food(veggie) => sad handler will go on to stack

  // Exercises
  // 1) recreate the Counter Actor with context.become


  case object Inc

  case object Dec

  case object Count

  class Counter extends Actor {
    override def receive: Receive = dispatch(0)

    def dispatch(i: Int): Receive = {
      case Inc => context.become(dispatch(i + 1))
      case Dec => context.become(dispatch(i - 1))
      case Count => println(f"Count is $i")
    }
  }

  val counter = system.actorOf(Props[Counter])
  counter ! Inc
  counter ! Inc
  counter ! Count
  counter ! Dec
  counter ! Count


  // 2) a simplified voting system

  case class Vote(candidate: String)

  case object VoteStatusRequest

  case class VoteStatusResponse(candidate: Option[String])

  class Citizen extends Actor {
    override def receive: Receive = didNotVoteReceive

    def votedReceive(candidate: String): Receive = {
      case VoteStatusRequest => sender ! VoteStatusResponse(Some(candidate))
      case Vote(_) => ()
    }

    def didNotVoteReceive: Receive = {
      case VoteStatusRequest => sender() ! VoteStatusResponse(None)
      case Vote(candidate) => context.become(votedReceive(candidate))
    }
  }

  case class AggVotes(citizens: Set[ActorRef])

  class VoteAgg extends Actor {
    override def receive: Receive = receiveStarted

    def receiveStarted: Receive = {
      case AggVotes(citizens) =>
        citizens.foreach(_ ! VoteStatusRequest)
        context.become(receiveWaiting(List.empty[Option[String]], citizens.size))
    }

    def receiveWaiting(votes: List[Option[String]], totalCitizens: Int): Receive = {
      case VoteStatusResponse(resp) =>
        val allVotes = resp :: votes
        if (allVotes.length == totalCitizens) {
          val v = allVotes.flatten.groupBy(identity).mapValues(_.size)
          println(f"votes are $v")
        } else {
          context.become(receiveWaiting(resp :: votes, totalCitizens))
        }
    }
  }

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Martin")

  val voteAgg = system.actorOf(Props[VoteAgg])
  voteAgg ! AggVotes(Set(alice, bob, charlie)) // print the status of votes

}
