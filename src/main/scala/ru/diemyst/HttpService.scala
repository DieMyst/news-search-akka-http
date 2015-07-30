package ru.diemyst

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

import scala.concurrent.duration._
import spray.json.DefaultJsonProtocol

/**
 * Created by dshakhtarin on 30.07.2015.
 */
case class News(news: List[Query])
case class Query(query: String, n: Int)
case class Response(date: String, result: Seq[References])
case class References(site: String, n: Int)

object Protocols extends DefaultJsonProtocol with SprayJsonSupport{
  implicit def queryFormat = jsonFormat2(Query.apply)
  implicit def newsFormat = jsonFormat1(News.apply)
}

class HttpService(implicit system: ActorSystem) {
  import Protocols._

  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(60.seconds)

  val log = Logging(system, classOf[HttpService])

  val host = "127.0.0.1"
  val port = 8080

  val route: Route = {
    pathPrefix("analyzenews") {
      (post & entity(as[News])) { news =>
        complete {
          println(news)
          StatusCodes.OK
        }
      }
    }
  }


  val serverBinding = Http(system).bindAndHandle(interface = host, port = port, handler = route).onSuccess {
    case _ => log.info(s"akka-http server started on $host:$port")
  }
}

object Testing extends App {
  implicit val system = ActorSystem("test-system", ConfigFactory.parseString( """
                                                                              """.stripMargin))
  new HttpService()
}

