package com.bestmile
package persistence

import com.github.tminglei.slickpg._

trait PostgresDriver extends ExPostgresProfile with PgCirceJsonSupport {
  def pgjson = "jsonb"

  override val api = MyAPI

  object MyAPI extends API  with CirceImplicits
}

object PostgresDriver extends PostgresDriver