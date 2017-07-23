package com.bestmile
package persistence

import scala.concurrent.Future
import PostgresDriver.api._
import model.Event
import model._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._

import EventTable.query

trait EventRepository {
  def write(events: Seq[Event]): Future[Boolean]
  def find(eventType: Event.Type): Future[Seq[Event]]
  def find(start: DateTime, end: DateTime): Future[Seq[Event]]
  def deleteEvents: Future[Int]
}

class OnDiskEventRepository(implicit val appContext: AppContext) extends EventRepository {
  implicit lazy val executionContext = appContext.executionContext
  lazy val db = appContext.database

  def write(events: Seq[Event]): Future[Boolean]= {
    db.run(DBIO.sequence(events.map(e => query += e)))
      .map(res => res.filter(_ == 1).length == events.length)
  }

  def find(eventType: Event.Type): Future[Seq[Event]] = {
    db.run(query.filter(_.json ?? eventType.toString).result)
  }

  // TODO: add an index for createdAt field
  // TODO: make the request in SQL
  def find(start: DateTime, end: DateTime): Future[Seq[Event]] = {
    db.run(query.result).map(_.filter{ x =>
      x.createdAt.getMillis() >= start.getMillis() &&
      x.createdAt.getMillis() <= end.getMillis()
    })
  }

  def deleteEvents: Future[Int] = db.run(query.delete)
}

class EventTable(tag: Tag) extends Table[Event](tag, "events") {
  def json = column[Json]("json")

  def * = (json) <> (
    { tuple: (Json) => tuple.as[Event].right.get },
    { up: Event => Some((up.asJson)) }
  )
}

object EventTable {
  val query = TableQuery[EventTable]
}