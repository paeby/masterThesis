package com.bestmile
package model

import enumeratum._

sealed trait Event {
  def createdAt: DateTime
}

// Raw (platform generated) events
case class Created(
 bookingID: Booking.ID,
 userID: User.ID,
 createdAt: DateTime,
 siteID: Site.ID,
 seatCount: Int
) extends Event

case class DroppedOff(
  bookingID: Booking.ID,
  userID: User.ID,
  createdAt: DateTime,
  siteID: Site.ID,
  seatCount: Int
) extends Event

case class PickedUp(
  bookingID: Booking.ID,
  userID: User.ID,
  createdAt: DateTime,
  siteID: Site.ID,
  seatCount: Int,
  vehicleID: Vehicle.ID
) extends Event

case class VehicleAttrib(
  createdAt: DateTime,
  siteID: Site.ID,
  vehicleID: Vehicle.ID,
  location: Option[Location],
  orientation: Option[Double],
  speed: Option[Double],
  battery: Option[Battery]
) extends Event

case class Ignore(
  createdAt: DateTime
) extends Event

// Reporting Events
case class UserWait(
   createdAt: DateTime,
   booking: Created,
   picked: PickedUp
) extends Event

case class UserJourney(
  createdAt: DateTime,
  created: Created,
  picked: PickedUp,
  dropped: DroppedOff
) extends Event

object Event {

  // platform events
  def apply(b: BookingEnvelope): Seq[Event] = Seq(
    b.entity.status match {
      case Booking.Status.Wait => Created(b.entity.bookingID, b.entity.userID, b.entity.timestamp, b.entity.siteID, b.entity.seatCount)
      case Booking.Status.Arrived => {
        b.entity.vehicleID match {
          case Some(v) => PickedUp(b.entity.bookingID, b.entity.userID, b.entity.timestamp, b.entity.siteID, b.entity.seatCount, v)
          case _ => PickedUp(b.entity.bookingID, b.entity.userID, b.entity.timestamp, b.entity.siteID, b.entity.seatCount, null)
        }
      }
      case Booking.Status.Done => DroppedOff(b.entity.bookingID, b.entity.userID, b.entity.timestamp, b.entity.siteID, b.entity.seatCount)
      case _ => Ignore(b.entity.createdAt)
    }
  )

  def apply(vs: VehicleStatus): Seq[Event] =
    vs.messages.map{ x =>
      VehicleAttrib(
        DateTime.fromInstant(x.timestamp),
        vs.siteID,
        x.vehicleID,
        x.attributes.location,
        x.attributes.orientation,
        x.attributes.speed,
        x.attributes.battery)
    }

  // reporting events
  def apply(createdAt: DateTime, booking: Created, picked: PickedUp) = new UserWait(createdAt, booking, picked)
  def apply(createdAt: DateTime, created: Created, picked: PickedUp, dropped: DroppedOff) = new UserJourney(createdAt, created, picked, dropped)

  def toString(event: Event) = event match {
    case DroppedOff(bookingID, userID, createdAt, siteID, seatCount) =>
      s"DroppedOff,$bookingID,$userID,$createdAt,$siteID,$seatCount\n"
    case PickedUp(bookingID, userID, createdAt, siteID, seatCount, vehicleID) =>
      s"PickedUp,$bookingID,$userID,$createdAt,$siteID,$seatCount,$vehicleID\n"
    case Created(bookingID, userID, createdAt, siteID, seatCount) =>
      s"Created,$bookingID,$userID,$createdAt,$siteID,$seatCount\n"
    case VehicleAttrib(createdAt, siteID, vehicleID, location, orientation, speed, battery) =>
      (location, orientation, speed, battery) match {
        case (Some(Location(position)), Some(ang), Some(s), Some(Battery(level, state))) =>
          s"$vehicleID,$createdAt,$siteID,${position.x},${position.y},$ang,$s,$level,$state\n"
        case _ => "INVALID"
      }
    case _ => "Non-platform event\n"
  }

  def isBooking(e: Event) = e match {
    case Created(_,_,_,_,_) => true
    case DroppedOff(_,_,_,_,_) => true
    case PickedUp(_,_,_,_,_,_) => true
    case _ => false
  }

  def isVehicle(e: Event) = e match {
    case VehicleAttrib(_,_,_,_,_,_,_) => true
    case _ => false
  }

  sealed abstract class Type extends EnumEntry

  object Type extends CirceEnum[Type] with Enum[Type] {

    case object Created extends Type
    case object DroppedOff extends Type
    case object PickedUp extends Type

    val values = findValues
  }
}
