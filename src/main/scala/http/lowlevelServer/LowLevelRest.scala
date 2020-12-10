package http.lowlevelServer

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import http.lowlevelServer.GuitarDb.{AddFail, AddInventory, AddStatus, AddSuccess, CreateGuitar, FindAllGuitars, FindGuitar, GetInventory, GuitarCreated}
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future


case class Guitar(make: String, model: String, quantity: Int = 0)


object GuitarDb {

  case class CreateGuitar(guitar: Guitar)

  case class GuitarCreated(id: Int)

  case class FindGuitar(id: Int)

  case object FindAllGuitars

  case class GetInventory(inStock: Boolean)

  case class AddInventory(id: Int, quantity: Int)

  trait AddStatus

  case object AddSuccess extends AddStatus
  case object AddFail extends AddStatus

}

class GuitarDb extends Actor with ActorLogging {

  import GuitarDb._

  var guitars: Map[Int, Guitar] = Map.empty[Int, Guitar]
  var currentGuitarId = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList
    case FindGuitar(id) =>
      log.info(s"Searching guitar by id $id")
      sender() ! guitars.get(id)
    case CreateGuitar(g) =>
      log.info(f"Adding guitar $g with id $currentGuitarId")
      guitars += (currentGuitarId -> g)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case GetInventory(inStock) =>
      log.info(s"Getting inStock $inStock inventory")
      if (inStock) sender() ! guitars.values.filter(_.quantity > 0)
      else sender() ! guitars.values.filter(_.quantity ==  0)
    case AddInventory(id, quantity) =>
      log.info(s"Updating inventory for $id")
      guitars.get(id) match {
        case None => sender() ! AddFail
        case Some(g) =>
          guitars += (id -> g.copy(quantity = g.quantity + quantity))
          sender() ! AddSuccess
      }
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {

  implicit val guitarFormat = jsonFormat3(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("low-level-rest")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  /*
  GET localhost:8000/api/guitar list
  POST localhost:8000/api/guitar save to db
  GET localhost:8000/api/guitar?id=xxx => get by id

  uses json
   */

  val simpleGuitar = Guitar("fender", "strata")
  println(simpleGuitar.toJson.prettyPrint)

  val simpleGuitarSting =
    """
      |{
      |  "make": "fender",
      |  "model": "strata",
      |  "quantity": 0
      |}
    """.stripMargin

  println(simpleGuitarSting.parseJson.convertTo[Guitar])

  val guitarDb = system.actorOf(Props[GuitarDb], "guitarbd")

  val guitarList = List(
    Guitar("fender", "strata"),
    Guitar("gibson", "les paul"),
    Guitar("martin", "lx1"),
  )

  guitarList.foreach { guitar => guitarDb ! CreateGuitar(guitar) }
  implicit val defaultTimeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt)
    guitarId match {
      case None => Future.successful(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture = (guitarDb ? FindGuitar(id)) .mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) => HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitar.toJson.prettyPrint
            )
          )
        }
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _) =>
      /*
      query param handling code here
       */
      val query = uri.query() // is a maplike obj
      if (query.isEmpty) {
      val guitarsFuture = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
      guitarsFuture.map(guitars => HttpResponse(
        entity = HttpEntity(
          ContentTypes.`application/json`,
          guitars.toJson.prettyPrint
        )
      ))
      }
      else getGuitar(query)
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      // entities are Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitar= strictEntity.data.utf8String.parseJson.convertTo[Guitar]
        val created = (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        created.map(_ => HttpResponse(StatusCodes.OK))
      }
    case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), _, entity, _) =>
      val query = uri.query() // is a maplike obj
      if (query.isEmpty) {
        Future.successful(HttpResponse(StatusCodes.NotFound))
      }
      else {
        val inStockO= query.get("inStock").map(_.toBoolean)
        inStockO match {
          case None => Future.successful(HttpResponse(StatusCodes.NotFound))
          case Some(inStock) =>
            val guitarsFuture = (guitarDb ? GetInventory(inStock)).mapTo[List[Guitar]]
            guitarsFuture.map(guitars => HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            ))
        }
      }
    case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, entity, _) =>
      val query = uri.query()
      if (query.isEmpty) {
        Future.successful(HttpResponse(StatusCodes.NotFound))
      }
      else {
        val idO = query.get("id").map(_.toInt)
        val quantO = query.get("quantity").map(_.toInt)

        (idO, quantO) match {
          case (_, None) |  (None, _) => Future.successful(HttpResponse(StatusCodes.NotFound))
          case (Some(id), Some(q)) =>
            val addResult = (guitarDb ? AddInventory(id, q)).mapTo[AddStatus]
            addResult.map {
              case AddSuccess => HttpResponse(StatusCodes.OK)
              case AddFail => HttpResponse(StatusCodes.BadRequest)
            }
        }
      }
    case req: HttpRequest =>
      req.discardEntityBytes()
      Future.successful(HttpResponse(status = StatusCodes.NotFound))
  }

  // EX: add quantity field to each guitar

  Http().bindAndHandleAsync(requestHandler, "localhost", 8000)

}
