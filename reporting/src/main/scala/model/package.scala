package com.bestmile

import io.circe._
import org.joda.time.Duration
import org.joda.time.format.ISODateTimeFormat
import com.vividsolutions.jts

package object model {

  object User {
    type ID = String
  }

  object Site {
    type ID = UUID
  }

  object Vehicle {
    type ID = UUID
  }

  type UUID = com.eaio.uuid.UUID

  type Duration = org.joda.time.Duration
  type DateTime = org.joda.time.DateTime
  val UTC = org.joda.time.DateTimeZone.UTC

  type Coordinate = jts.geom.Coordinate

  object DateTime {
    val isoDateTimeFormatter = ISODateTimeFormat.dateTime.withZone(UTC)
    def now = org.joda.time.DateTime.now(UTC)
    def fromInstant(instant: Long): DateTime = new org.joda.time.DateTime(instant, UTC)
    def toISO(dt: DateTime): String = isoDateTimeFormatter.print(dt)
    def fromISO(iso: String): Either[String,DateTime] =
      try { Right(isoDateTimeFormatter.parseDateTime(iso)) } catch { case e: Throwable => Left("Invalid DateTime ISO") }
  }

  object Duration {
    val zero = org.joda.time.Duration.ZERO
    def apply(start: DateTime, end: DateTime): Duration = new Duration(start, end)
  }

  implicit val dateTimeEncoder: Encoder[DateTime] = Encoder[String].contramap(x => DateTime.toISO(x))
  implicit val dateTimeDecoder: Decoder[DateTime] = Decoder[String].emap(str => DateTime.fromISO(str))

  implicit val encodeUUID: Encoder[UUID] = Encoder[String].contramap(_.toString)
  implicit val decodeUUID: Decoder[UUID] = Decoder[String].emap(x => Right(new UUID(x)))

  implicit val coordinateEncoder: Encoder[Coordinate] = Encoder[Tuple2[Double,Double]].contramap(c => c.x -> c.y)
  implicit val coordinateDecoder: Decoder[Coordinate] = Decoder[Tuple2[Double,Double]].emap(t => Right(new Coordinate(t._1, t._2)))

  implicit val bookingStatusEncoder: Encoder[Booking.Status] =
    Encoder[String].contramap(x => x match {
      case s if s == Booking.Status.Wait => "wait"
      case s if s == Booking.Status.InProgress => "inprogress"
      case s if s == Booking.Status.Approaching => "approaching"
      case s if s == Booking.Status.Arrived => "arrived"
      case s if s == Booking.Status.Done => "done"
    })
  implicit val bookingStatusDecoder: Decoder[Booking.Status] =
    Decoder[String].emap(str => str match {
      case x if x == "wait" => Right(Booking.Status.Wait)
      case x if x == "inprogress" => Right(Booking.Status.InProgress)
      case x if x == "approaching" => Right(Booking.Status.Approaching)
      case x if x == "arrived" => Right(Booking.Status.Arrived)
      case x if x == "done" => Right(Booking.Status.Done)
      case _ => Left(s"Unexpected booking status $str")
    })
}