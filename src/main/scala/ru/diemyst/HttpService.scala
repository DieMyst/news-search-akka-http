package ru.diemyst

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.{Future, ExecutionContext}
import ExecutionContext.Implicits.global

import scala.concurrent.duration._
import spray.json.DefaultJsonProtocol

/**
 * Created on 30.07.2015.
 * News search from Google API
 */
case class Request(news: List[Query])
case class Query(query: String, n: Int)
case class Response(date: String, result: Seq[References])
case class References(site: String, n: Int)

object Protocols extends DefaultJsonProtocol with SprayJsonSupport{
  implicit def queryFormat = jsonFormat2(Query.apply)
  implicit def newsFormat = jsonFormat1(Request.apply)
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
      (post & entity(as[Request])) { request =>
        complete {
          println(request)
          val splits = request.news.map(q => (q.query.split(" ").map(str => ("q", str)), q.n))
          val listOfFutures = splits.map { pair =>
            val link = Uri("https://ajax.googleapis.com/ajax/services/search/news?v=1.0").withQuery(pair._1 :+("v", "1.0"): _*)
            println(link)
            Http().singleRequest(HttpRequest(GET, link))
              .flatMap(response => Unmarshal(response.entity).to[String])
              .map(println)
          }
          Future.sequence(listOfFutures)

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


