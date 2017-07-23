package com.bestmile

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaTypes}
import persistence.DDL
import service.EventService
import model._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import com.typesafe.config.ConfigFactory
import java.io._
import sys.process._

trait WebService {
  implicit def appContext: AppContext
  implicit def ec = appContext.executionContext
  implicit def ma = appContext.actorMaterializer
  val eventService: EventService
  lazy val logger = appContext.actorSystem.log


  val route = {
    def getInterval(start: Option[String], end: Option[String]) = (start, end) match {
      case (Some(s), Some(e)) => new DateTime(s) -> new DateTime(e)

      case (None, Some(e)) =>
        val ed = new DateTime(e)
        ed.minusHours(24) -> ed

      case (Some(s), None) =>
        val st = new DateTime(s)
        st -> st.plusHours(24)

      case (None, None) => DateTime.now.minusHours(24) -> DateTime.now.plusHours(24)
    }
    lazy val eventInterval: Long = appContext.config.getString("logInterval").toLong

    path("bookings") {
      (post & entity(as[Booking])) { booking =>
        complete {
          eventService.handleFakeKafka(booking).map(_ => "Ok")
        }
      }
    } ~
    path("vehiclestatus") {
      (post & entity(as[VehicleStatus])) { vstatus =>
        complete {
          eventService.handleFakeKafka(vstatus).map(_ => "Ok")
        }
      }
    } ~
    path("events") {
      (get & parameters(('start.?, 'end.?))) { (start, end) =>
        val bounds = getInterval(start, end)
        complete {
          eventService.find(bounds._1, bounds._2).map(events => generateResponseEvent(events))
        }
      }
    } ~
    path("summarystats") {
      (get & parameters(('start.?, 'end.?))) { (start, end) =>
        val bounds = getInterval(start, end)
        complete {
          eventService.summaryStats(bounds._1, bounds._2).map(resp => generateResponseString(resp))
        }
      }
    } ~
    path("journeylog") {
      (get & parameters(('start.?, 'end.?))) { (start, end) =>
        val bounds = getInterval(start, end)
        complete {
          eventService.journeyLog(bounds._1, bounds._2).map(resp => generateResponseString(
            "Booking Time,User ID,Pick-up Time,Drop-off Time,Seat Count,Vehicle ID\n" +: resp))
        }
      }
    } ~
    path("vehiclestatuslog") {
      (get & parameters(('start.?, 'end.?, 'interval.?))) { (start, end, interval) =>
        val bounds = getInterval(start, end)
        val duration = new Duration(interval.map(_.toLong).getOrElse(eventInterval))
        complete {
          eventService.vehicleLog(bounds._1, bounds._2, duration).map(resp => generateResponseString(
            "Vehicle ID,TimeStamp,Site ID,Location Latitude,Location Longitude,Orientation,Speed,Battery Level,Battery State\n" +: resp
              .groupBy(_.vehicleID).toList
              .flatMap(_._2.map(Event.toString)))
          )
        }
      }
    } ~
    path("vehicleplots") {
      (get & parameters(('start.?, 'end.?, 'interval.?))) { (start, end, interval) =>
        val bounds = getInterval(start, end)
        val duration = new Duration(interval.map(_.toLong).getOrElse(eventInterval))
        complete{
          eventService.vehicleLog(bounds._1, bounds._2, duration).map(resp => {
            val pw = new PrintWriter(new FileOutputStream("plots/vehicle_logs.csv", false))
            pw.write(("Vehicle ID,TimeStamp,Site ID,Location Latitude,Location Longitude,Orientation,Speed,Battery Level,Battery State\n" +: resp
              .groupBy(_.vehicleID).toList
              .flatMap(_._2.map(Event.toString))).mkString)
            pw.close
          })
          eventService.journeyLog(bounds._1, bounds._2).map(resp => {
            val pw = new PrintWriter(new FileOutputStream("plots/journey_logs.csv", false))
            pw.write(("Booking Time,User ID,Pick-up Time,Drop-off Time,Seat Count,Vehicle ID\n" +: resp).mkString)
            pw.close
          })
          val cmd = Seq("python", "plots/vehicles.py")
          val ex = cmd.!!
          println(ex)
          val plot = new File("plots/vehicles.pdf").getAbsoluteFile
          HttpResponse(
            entity = HttpEntity.fromPath(MediaTypes.`application/pdf`, plot.toPath))
        }
      }
    } ~
    path("journeyplots") {
      (get & parameters(('start.?, 'end.?, 'interval.?))) { (start, end, interval) =>
        val bounds = getInterval(start, end)
        complete {
          eventService.journeyLog(bounds._1, bounds._2).map(resp => {
            val pw = new PrintWriter(new FileOutputStream("plots/journey_logs.csv", false))
            pw.write(("Booking Time,User ID,Pick-up Time,Drop-off Time,Seat Count,Vehicle ID\n" +: resp).mkString)
            pw.close
          })
          val cmd = Seq("python", "plots/journeys.py")
          val ex = cmd.!!
          println(ex)
          val plot = new File("plots/journeys.pdf").getAbsoluteFile
          HttpResponse(
            entity = HttpEntity.fromPath(MediaTypes.`application/pdf`, plot.toPath))
        }
      }
    } ~ path("deleteevents") {
          val deleted =  eventService.deleteEvents
          complete {
            deleted.map(result => {
              val message: String = "Number of deleted events: " + result
              generateResponseString(Seq(message))
            })
          }
    } ~ pathPrefix("ui") {
        getFromResourceDirectory("")
    } ~ path("") {
      redirect("ui/index.html", StatusCodes.MovedPermanently)
    }
  }

  private def generateResponseString(lines: Seq[String]): HttpResponse = {
    val chunkSource = akka.stream.scaladsl.Source(lines.toList)
      .map(ByteString.apply)
      .map(HttpEntity.ChunkStreamPart.apply)
    HttpResponse(entity = HttpEntity.Chunked(`text/plain(UTF-8)`, chunkSource))
  }

  private def generateResponseEvent(events: Seq[Event]): HttpResponse = {
    val lines = events
      .groupBy{
        case e if Event.isBooking(e) => 0
        case e if Event.isVehicle(e) => 1
        case _ => 2}
      .flatMap( x => x._1 match {
        case 0 =>
          "Type, Booking ID, User ID, TimeStamp, Site ID, Seat Count\n" +: x._2.map(Event.toString)
        case 1 =>
          "Vehicle ID, TimeStamp, Site ID, Location(x, y), Orientation, Speed, Battery(level, state)\n" +: x._2.map(Event.toString)
        case _ => "Unknown" +: x._2.map(Event.toString)
      })

    generateResponseString(lines.toSeq)
  }
}

object WebServer extends App with WebService {
  val config = ConfigFactory.load()

  implicit val actorSystem = ActorSystem("system")
  implicit val actorMaterializer = ActorMaterializer()
  implicit override val appContext = AppContext(actorSystem, actorMaterializer,
    actorSystem.dispatcher, DDL.createDBConnection(config), config)
  override val eventService = new EventService

  Http().bindAndHandle(route, appContext.config.getString("http.hostname"), appContext.config.getInt("http.port"))
}
