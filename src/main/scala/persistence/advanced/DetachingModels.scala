package persistence.advanced

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object DomainModel {

  case class User(id: String, email: String, name: String)

  case class Coupon(code: String, amount: Int)

  case class ApplyCoupon(coupon: Coupon, user: User)

  case class CouponApplied(code: String, user: User)

}

object DataModel {
  case class WrittenCouponApplied(code: String, userId: String, userEmail: String)
  case class WrittenCouponAppliedV2(code: String, userId: String, userEmail: String, name: String)
}

class ModelAdapter extends EventAdapter {
  import DataModel._
  import DomainModel._

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case e @ WrittenCouponApplied(code, id, email) =>
      println(f"converting event $e to domain model")
      EventSeq.single(CouponApplied(code, User(id, email, "")))
    case e @ WrittenCouponAppliedV2(code, id, email, name) =>
      println(f"converting event $e to domain model")
      EventSeq.single(CouponApplied(code, User(id, email, name)))
    case other => EventSeq.single(other)
  }

  override def toJournal(event: Any): Any = event match {
    case e @  CouponApplied(code, user) =>
      println(f"converting event $e to data model")
      WrittenCouponAppliedV2(code, user.id, user.email, user.name)
  }

  override def manifest(event: Any): String = "CMA"
}


object DetachingModels extends App {
  import DomainModel._

  class CouponManager extends PersistentActor with ActorLogging {

    val coupons = new mutable.HashMap[String, User]()

    override def persistenceId: String = "coupon-manager"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) { e =>
            log.info(f"persisted $e")
            coupons.put(coupon.code, user)
          }
        }
    }

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(f"recovered $event")
        coupons.put(code, user)
    }
  }

  val system = ActorSystem("detachingModels", ConfigFactory.load().getConfig("detachingModels"))

  val manager = system.actorOf(Props[CouponManager], "manager")

   // (0 to 10).foreach(i => manager ! ApplyCoupon(Coupon(s"coup2 $i", 2), User(i.toString, s"$i@gmail.com", "name")))

}
