package com.bestmile

import akka.event.NoLogging
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest, WSProbe}
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, MediaTypes}
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, Materializer}
import akka.actor.{Actor, ActorSystem, Props}
import org.scalatest._
import persistence.PostgresDriver.api._
import persistence.DDL
import scala.concurrent.duration._
import service.EventService
import model._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.collection.immutable.Range.Inclusive

import com.typesafe.config.ConfigFactory

class WebServerSpec extends WordSpec with Matchers with ScalatestRouteTest with WebService {
  import WebServerSpec._
  implicit val actorSystem = ActorSystem("system")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  val config = ConfigFactory.load()
  implicit override val appContext = AppContext(actorSystem, actorMaterializer, executionContext, DDL.createDBConnection(config), config)
  override val eventService = new EventService

  implicit def default = RouteTestTimeout(new DurationInt(5).second)

  "WebServer" should {

    val (journeys, awt, np) = fakeJourneys(10)
    val bookingEvents = journeys.flatMap(x => List(x.created, x.picked, x.dropped))
    val (vehicles, nmessages, interval) =
      ( List(new UUID("d86cea08-ce9d-11e6-bf26-cec0c932ce01"),
          new UUID("fec90af6-d0d7-11e6-bf26-cec0c932ce01"),
          new UUID("051d948a-d0d8-11e6-bf26-cec0c932ce01")),
        100,
        new model.Duration(1000) )
    val messages = fakeVehicleMessages(vehicles, nmessages, interval)

    "create booking" in {
      for {
        j <- bookingEvents
        postRequest = HttpRequest(HttpMethods.POST, uri = "/bookings",
          entity = HttpEntity(MediaTypes.`application/json`, j.asJson.noSpaces))
      }
        postRequest ~> route ~> check {
          status shouldBe OK
        }
    }

    "filter events" in {
      Get("/events") ~> route ~> check {
        println(response)
        status shouldBe OK
      }
    }

    "compute summary stats" in {
      val (start, end) = bookingEvents.head.timestamp -> bookingEvents.last.timestamp
      Get(s"/summarystats?start=${DateTime.toISO(start)}&end=${DateTime.toISO(end)}") ~> route ~> check {
        status shouldBe OK
        responseAs[String] contains s"Average Wait Time, ${awt/(60*1000)} minutes"
        responseAs[String] contains s"Number of Passengers, $np"
      }
    }

    "compute journey log" in {
      val (start, end) = bookingEvents.head.timestamp -> bookingEvents.last.timestamp
      Get(s"/journeylog?start=${DateTime.toISO(start)}&end=${DateTime.toISO(end)}") ~> route ~> check {
        status shouldBe OK
        responseAs[String].split('\n').length shouldEqual (journeys.length + 1)
//        responseAs[String] shouldEqual journeys.foldRight("") { (j, s) =>
//          s ++ s"${j.picked.userID},${DateTime.toISO(j.picked.createdAt)},${DateTime.toISO(j.dropped.createdAt)}\n"
//        }
      }
    }

    "create vehicle status" in {
      for {
        j <- messages
        postRequest = HttpRequest(HttpMethods.POST, uri = "/vehiclestatus",
          entity = HttpEntity(MediaTypes.`application/json`, j.asJson.noSpaces))
      }
        postRequest ~> route ~> check {
          status shouldBe OK
        }
    }

    "compute vehicle interval log" in {
      val (start, end) = DateTime.fromInstant(messages.head.messages.head.timestamp) -> DateTime.fromInstant(messages.last.messages.head.timestamp)
      val factor = 10
      val qinterval = interval.multipliedBy(factor)

      Get(s"/vehiclestatuslog?start=${DateTime.toISO(start)}&end=${DateTime.toISO(end)}&interval=${qinterval.getMillis()}") ~> route ~> check {
        status shouldBe OK
        responseAs[String].split('\n').length shouldEqual ((nmessages/factor).toInt * vehicles.length + 1)
      }
    }
  }
}

object WebServerSpec {

  def bookingWithStatus(status: Booking.Status): Booking =
    Booking(
      vehicleID = Some(new UUID("e0e20140-0153-11e6-98f4-c68580c5f50a")),
      bookingID = new UUID("d86cea08-ce9d-11e6-bf26-cec0c932ce01"),
      createdAt = DateTime.now,
      timestamp = DateTime.now,
      userID = "fec90af6-d0d7-11e6-bf26-cec0c932ce01",
      siteID = new UUID("051d948a-d0d8-11e6-bf26-cec0c932ce01"),
      seatCount = 2,
      status = status
    )

  case class Journey(created: Booking, picked: Booking, dropped: Booking)
  def fakeJourneys(num: Int): (List[Journey], Double, Int) = {
    val id = new UUID("d86cea08-ce9d-11e6-bf26-cec0c932ce01")
    val vID = new UUID("e0e20140-0153-11e6-98f4-c68580c5f50a")
    val userID = "fec90af6-d0d7-11e6-bf26-cec0c932ce01"
    val siteID = new UUID("051d948a-d0d8-11e6-bf26-cec0c932ce01")
    val seatCount = 2
    val wait = 2 * 60 * 1000
    val duration = 5 * 60 * 1000

    def journeyList(list: List[Journey], now: Long): List[Journey] = list match {
      case xs if (xs.length == num) => list
      case xs => journeyList(list :+ Journey(
        Booking(id, DateTime.fromInstant(now), DateTime.fromInstant(now), userID, siteID, seatCount, Booking.Status.Wait, Some(vID)),
        Booking(id, DateTime.fromInstant(now), DateTime.fromInstant(now+wait), userID, siteID, seatCount, Booking.Status.Arrived, Some(vID)),
        Booking(id, DateTime.fromInstant(now), DateTime.fromInstant(now+wait+duration), userID, siteID, seatCount, Booking.Status.Done, Some(vID))
      ), now + wait + duration)
    }

    (journeyList(List(), DateTime.now.getMillis), 0.0 + wait, seatCount*num)
  }

  def fakeVehicleMessages(vehicles: List[Vehicle.ID], nmessages: Int, interval: model.Duration): List[VehicleStatus] = {
    val start = DateTime.now
    val siteID = new UUID("051d948a-d0d8-11e6-bf26-cec0c932ce01")
    val location = Some(Location(new Coordinate(1.0,2.0,3.0)))
    val orientation = Some(1.15)
    val speed = Some(5.0)
    val battery = Some(Battery(0.8, "Discharging"))
    vehicles.flatMap { x =>
      (1 to nmessages).toList
        .map { idx => VehicleStatus(
          siteID,
          List(
            Attributes(
              x,
              start.plus(interval.multipliedBy(idx)).getMillis(),
              VehicleAttributes(location, orientation, speed, battery)
            )
          )
        )}
    }
  }
}