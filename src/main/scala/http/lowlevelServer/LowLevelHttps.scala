package http.lowlevelServer

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import http.lowlevelServer.LowLevelHttps.getClass
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsConnectionContext {
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoryFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  val password = "akka-https".toCharArray // get from secure place

  ks.load(keyStoryFile, password)

  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509") // public key infra
  keyManagerFactory.init(ks, password)

  // initialize a trust manager

  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // step 4 Init SSL context

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  // return the https connection context

  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)
}

object LowLevelHttps extends App {

  implicit val system = ActorSystem("low-level-https")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // key store object



  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello from Akka Https
            |</body>
            |</html>
          """.stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity =  HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Oops
            |</body>
            |</html>
          """.stripMargin
        )
      )
  }

  val httpsBinding = Http().bindAndHandleSync(requestHandler, "localhost", 8443, HttpsConnectionContext.httpsConnectionContext)

}
