package com.bestmile
package model

import enumeratum._

// Booking mimics platform model: com.bestmile.model.Booking
// It must be kept in sync !!

case class BookingEnvelope(
  command: String,
  entity: Booking
)

case class Booking(
  bookingID: Booking.ID,
  createdAt: DateTime,
  timestamp: DateTime,
  userID: User.ID,
  siteID: Site.ID,
  seatCount: Int,
  status: Booking.Status,
  vehicleID: Option[Vehicle.ID]
)

object Booking {
  type ID = UUID

  sealed trait Status extends EnumEntry

  object Status extends CirceEnum[Status] with Enum[Status] {

    case object Wait extends Status
    case object Approaching extends Status
    case object Arrived extends Status
    case object InProgress extends Status
    case object Done extends Status

    val values = findValues
  }
}

