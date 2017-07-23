package com.bestmile
package persistence

import com.typesafe.config.Config
import config.ConfigKeys
import PostgresDriver.api._
import com.zaxxer.hikari.{HikariDataSource, HikariConfig}

object DDL {

  def createAdminDBConnection(config: Config): Database = {
    val hostname = ConfigKeys.Postgresql.hostname(config)
    val port = ConfigKeys.Postgresql.port(config)
    val url = s"jdbc:postgresql://${hostname}:${port}/"
    Database.forURL(
      url,
      user = ConfigKeys.Postgresql.role(config),
      password = ConfigKeys.Postgresql.password(config),
      driver = "org.postgresql.Driver"
    )
  }

  def withAdminDBConnection[A](config: Config)(f: Database => A): A = {
    val db = createAdminDBConnection(config)
    try { f(db) } finally db.close
  }

  def createDB(db: String) = sqlu"CREATE DATABASE #$db;"
  def dropDB(db: String) = sqlu"DROP DATABASE IF EXISTS #$db;"

  def createDBConnection(config: Config): Database = {
    val hostname = ConfigKeys.Postgresql.hostname(config)
    val port = ConfigKeys.Postgresql.port(config)
    val databaseName = ConfigKeys.Postgresql.databaseName(config)
    val url = s"jdbc:postgresql://${hostname}:${port}/$databaseName"

    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(url)
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    hikariConfig.setUsername(ConfigKeys.Postgresql.role(config))
    hikariConfig.setPassword(ConfigKeys.Postgresql.password(config))
    Database.forDataSource(new HikariDataSource(hikariConfig))
  }

}
