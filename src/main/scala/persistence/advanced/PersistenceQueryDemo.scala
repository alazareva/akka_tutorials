package persistence.advanced

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

object PersistenceQueryDemo extends App {

  val system = ActorSystem("persistenceQuery", ConfigFactory.load().getConfig("persistenceQuery"))

  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // gets all persistence ids, return Source
  implicit val materializer = ActorMaterializer()(system)

  //val persistenceIds = readJournal.persistenceIds()
  //persistenceIds.runForeach(id => println(id))

  class SimpleActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "simple-actor"

    override def receiveCommand: Receive = {
      case m => persist(m) { e =>
        log.info(s"saved $e")
      }
    }

    override def receiveRecover: Receive = {
      case m => log.info(s"recovered $m")
    }
  }

  import system.dispatcher

  // val simpleActor = system.actorOf(Props[SimpleActor], "simple")

  system.scheduler.scheduleOnce(5 seconds) {
   //  simpleActor ! "message2"
  }

 // val events = readJournal.eventsByPersistenceId("simple-actor", 0, Long.MaxValue)
  // events.runForeach(e => println(s"read $e"))

  // events by tags
  val genres = Array("pop", "rock", "disco")
  case class Song(artist: String, title: String, genre: String)
  case class Playlist(songs: List[Song])
  case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreActor extends PersistentActor with ActorLogging {

    var latestId = 0

    override def persistenceId: String = "music-store-actor"

    override def receiveCommand: Receive = {
      case Playlist(songs) => persist(PlaylistPurchased(latestId, songs)) { e =>
        log.info(s"saved $e")
        latestId += 1
      }
    }

    override def receiveRecover: Receive = {
      case PlaylistPurchased(id, _) =>
        latestId = id
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def toJournal(event: Any): Any = event match {
      case e @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(e, genres)
    }

    override def manifest(event: Any): String = "MS"
  }

  val checkoutActor = system.actorOf(Props[MusicStoreActor], "musicStore")
  val r = new Random()

  (0 to 10).foreach {_ =>
    val maxSongs = r.nextInt(5)
    val songs = (0 until maxSongs).map(i => Song(s"artist $i", s"title $i", genres(r.nextInt(genres.length))))
    // checkoutActor ! Playlist(songs.toList)
  }

  val rockPlaylists = readJournal.eventsByTag("disco", Offset.noOffset)
  rockPlaylists.runForeach(e => println(s"playlist $e"))
}
