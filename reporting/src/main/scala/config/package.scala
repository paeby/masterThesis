package com.bestmile
package config

import com.typesafe.config.Config

object ConfigKeys {

  object Postgresql {
    def hostname(config: Config): String = config.getString("postgresql.server.hostname")
    def port(config: Config): String = config.getString("postgresql.server.port")
    def role(config: Config): String = config.getString("postgresql.main.role")
    def password(config: Config): String = config.getString("postgresql.main.password")
    def databaseName(config: Config): String = config.getString("postgresql.main.db")
  }
}

