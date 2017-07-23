package com.bestmile
package model

// Vehicle Status mimics platform model: com.bestmile.model.broadcast.AttributesEnvelope, com.bestmile.model.resources.Attributes
// It must be kept in sync !!

case class VehicleStatus(
  siteID: Site.ID,
  messages: List[Attributes]
)

case class Attributes(
  vehicleID: Vehicle.ID,
  timestamp: Long,
  attributes: VehicleAttributes
)

case class VehicleAttributes(
  location: Option[Location],
  orientation: Option[Double],
  speed: Option[Double],
  battery: Option[Battery]
)

case class Location(
  position: Coordinate
)

case class Battery(
  level: Double,
  state: String
)