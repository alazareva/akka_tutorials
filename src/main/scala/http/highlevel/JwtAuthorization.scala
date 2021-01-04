package http.highlevel

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.RawHeader
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, headers}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import scala.util.Success

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, pw: String)

  implicit val loginRequestFormat = jsonFormat2(LoginRequest)
}

object JwtAuthorization extends App with SprayJsonSupport {

  implicit val system = ActorSystem("auth")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  import SecurityDomain._

  val superSecretPwDb = Map(
    "admin" -> "admin",
    "asiya" -> "dogs"
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "secretKey"

  def checkPassword(username: String, password: String): Boolean = {
    superSecretPwDb.contains(username) && superSecretPwDb(username) == password
  }
  def createToken(username: String, expirationInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("rtjvm")
      )
    JwtSprayJson.encode(claims, secretKey, algorithm)
  }

  def tokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
    case _ => true

  }
  def tokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))


  val loginRoute = post {
    entity(as[LoginRequest]) {
      case LoginRequest(username, password) if checkPassword(username, password) =>
        val token = createToken(username, 1)
        respondWithHeader(headers.RawHeader("Access-Token", token)) {
          complete(StatusCodes.OK)
        }
      case _ => complete(StatusCodes.Unauthorized)
    }
  }

  val authenticatedRoute = (path("secureEndpoint") & get) {
    optionalHeaderValueByName("Authorization") {
      case Some(token) if tokenExpired(token) =>
      complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "expired token"))
      case Some(token) if tokenValid(token) =>
      complete("User access authorized input")
      case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "token is invalid"))
    }
  }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8080)

}
