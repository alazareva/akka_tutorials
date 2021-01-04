package http.highlevel
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val format = jsonFormat3(Book)
}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport{

  var books = List(
    Book(1, "Bob", "about Bob"),
    Book(2, "Alice", "Harry Potter"),
    Book(3, "Mike", "The Office"),
    Book(3, "Ane", "The book"),
  )

  /*
    GET /api/book lists books
    GET /api/book/x get book by id
    GET /api/book?id=x same
    POST /api/book/ add new book
   */

  val bookRoute =
    pathPrefix("api" / "book") {
      (path("author"/ Segment) & get) { author =>
        complete(books.filter(_.author == author))
      } ~ get {
        (path(IntNumber) | parameter('id.as[Int])) { id =>
          complete(books.find(_.id == id))
        } ~ pathEndOrSingleSlash {
          complete(books)
        }
      } ~ post {
        entity(as[Book]) { book =>
          books = books :+ book
          complete(StatusCodes.OK)
        }
      }
    }

}

class RouteDSLSpec extends WordSpec with Matchers with ScalatestRouteTest with BookJsonProtocol {
  import RouteDSLSpec._

  "A library should" should {
    "return all the books in by author" in {
      Get("/api/book/author/Bob") ~> bookRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe List(Book(1, "Bob", "about Bob"))
      }
    }
    "return all the books in the library" in {
      Get("/api/book") ~> bookRoute ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[Book]] shouldBe books
      }
    }
    "return book by param" in {
      Get("/api/book?id=2") ~> bookRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Option[Book]] shouldBe Some(books(1))
      }
    }
    "return book by calling endpont with path" in {
      Get("/api/book/2") ~> bookRoute ~> check {
        response.status shouldBe StatusCodes.OK
        val strictEntityFuture = response.entity.toStrict(1 second)
        val strictEntity = Await.result(strictEntityFuture, 1 second)
        strictEntity.contentType shouldBe ContentTypes.`application/json`
        val book = strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
        book shouldBe Some(books(1))
      }
    }
    "insert a book into db" in {
      val newBook = Book(5, "Steve", "Earth")
      Post("/api/book", newBook)  ~> bookRoute ~> check {
        status shouldBe StatusCodes.OK
        books should contain(newBook)
      }
    }
    "not support any other methods" in {
      Delete("/api/book")  ~> bookRoute ~> check {
        rejections should not be empty
      }
    }
  }
}
