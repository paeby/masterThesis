package com.bestmile
package task

import persistence.PostgresDriver.api._
import persistence.EventTable
import persistence.DDL
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.config.ConfigFactory
import config.ConfigKeys

private object Common {
  def createSchema(db: Database): Future[_] = {
    for {
      _ <- db.run(EventTable.query.schema.create)
    } yield {}
  }
}

object ResetDatabase {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val databaseName = ConfigKeys.Postgresql.databaseName(config)
    DDL.withAdminDBConnection(config) { db =>
      val res = for {
        _ <- db.run(DDL.dropDB(databaseName))
        _ <- db.run(DDL.createDB(databaseName))
      } yield {}
      Await.result(res, 60.seconds)
    }
    val db = DDL.createDBConnection(config)
    try {
      val res = Common.createSchema(db)
      Await.result(res, 60.seconds)
    } finally {
      db.close
    }
  }
}
