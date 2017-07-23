package com.bestmile

import scala.concurrent.ExecutionContextExecutor
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import persistence.PostgresDriver.api._

case class AppContext(
  actorSystem: ActorSystem,
  actorMaterializer: ActorMaterializer,
  executionContext: ExecutionContextExecutor,
  database: Database,
  config: com.typesafe.config.Config
)