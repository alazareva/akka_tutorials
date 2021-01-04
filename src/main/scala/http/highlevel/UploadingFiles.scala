package http.highlevel

import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object UploadingFiles extends App {


  implicit val system = ActorSystem("files")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val filesRoute =
    (pathEndOrSingleSlash & get) {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   <form action="http://localhost:8080/upload" method="post" enctype="multipart/form-data">
            |   <input type="file" name="myFile">
            |   <button type="submit">Upload</button>
            |   </form>
            | </body>
            |</html>
          """.stripMargin
        )
      )
    } ~ (path("upload") & extractLog) { log =>
      // handle uploaded file
      // multipart/ form-data
      entity(as[Multipart.FormData]) { formdata =>
        // handle file payload
        val partsSource: Source[Multipart.FormData.BodyPart, Any] = formdata.parts

        val filePartsSink: Sink[Multipart.FormData.BodyPart, Future[Done]] = Sink.foreach[Multipart.FormData.BodyPart] {
          part =>
            if (part.name == "myFile") {
              val filePath = "src/main/resources/download/" + part.filename.getOrElse("tempfile_" + System.currentTimeMillis())
              val file = new File(filePath)
              log.info(s"Writing to $filePath")
              val fileContentsSource = part.entity.dataBytes
              val fileContentsSink = FileIO.toPath(file.toPath)
              fileContentsSource.runWith(fileContentsSink)
            }
        }

        val writeOpFuture = partsSource.runWith(filePartsSink)
        onComplete(writeOpFuture) {
          case Success(v) => complete("File uploaded")
          case Failure(exception)  => complete(s"Failed $exception")
        }
      }
    }

  Http().bindAndHandle(filesRoute, "localhost", 8080)

}
