package ru.diemyst

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
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

case class GoogleResponse(responseData: Option[ResponseData], responseDetails: Option[String], responseStatus: Int)
case class ResponseData(results: List[Results], cursor: Cursor)
case class Results(unescapedUrl: Option[String], relatedStories: Option[List[RelatedStories]])
case class RelatedStories(unescapedUrl: Option[String])
case class Cursor(pages: List[Pages], currentPageIndex: Int)
case class Pages(start: String, label: Int)

case class Response(date: String, result: List[References])
case class References(site: String, n: Int)

object Protocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit def queryFormat = jsonFormat2(Query.apply)
  implicit def newsFormat = jsonFormat1(Request.apply)

  implicit def pagesFormat = jsonFormat2(Pages.apply)
  implicit def cursorFormat = jsonFormat2(Cursor.apply)
  implicit def relatedStoriesFormat = jsonFormat1(RelatedStories.apply)
  implicit def resultsFormat = jsonFormat2(Results.apply)
  implicit def responseDataFormat = jsonFormat2(ResponseData.apply)
  implicit def googleResponseFormat = jsonFormat3(GoogleResponse.apply)
}

class HttpService(implicit system: ActorSystem) {
  import Protocols._

  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(60.seconds)

  val log = Logging(system, classOf[HttpService])

  val host = "127.0.0.1"
  val port = 8080
  val linkPattern = Uri("https://ajax.googleapis.com/ajax/services/search/news")
  val version = Array(("v", "1.0"))

  val route: Route = {
    pathPrefix("analyzenews") {
      (post & entity(as[Request])) { request =>
        complete {
          println(request)
          val splits = request.news.map(q => (q.query.split(" ").map(str => ("q", str)), q.n))
          val listOfFutures = splits.map { pair =>
            val numberOfRequests = pair._2 / 8
            val tailrequest = pair._2 - numberOfRequests

            val f = List.fill(numberOfRequests)(8).::(tailrequest).map{num =>
              val link = linkPattern.withQuery(version ++ pair._1 :+ ("rsz", num.toString): _*)
              println(link)
              Http().singleRequest(HttpRequest(GET, link))
                .flatMap(response => Unmarshal(response.entity.withContentType(ContentTypes.`application/json`)).to[GoogleResponse])
                .recover{case x: Throwable => x.getLocalizedMessage}
            }
            f
          }
          Future.sequence(listOfFutures).map{ x =>
            println(x)
            x.mkString
          }


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
                                                                                |akka {
                                                                                |  loggers = ["akka.event.Logging$DefaultLogger"]
                                                                                |  loglevel = "DEBUG"
                                                                                |}
                                                                              """.stripMargin))
  new HttpService()
}


