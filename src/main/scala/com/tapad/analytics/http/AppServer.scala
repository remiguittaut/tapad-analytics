package com.tapad.analytics.http

import com.tapad.analytics.ingest.services.IngestService
import zio.http.{ Server, ServerConfig }
import zio.macros.accessible
import zio.{ Task, URLayer, ZIO, ZLayer }

@accessible
trait AppServer {
  val start: Task[Nothing]
}

object AppServer {

  val live: URLayer[IngestService with ServerConfig, AppServer] =
    ZLayer.fromZIO {
      for {
        config        <- ZIO.service[ServerConfig]
        ingestService <- ZIO.service[IngestService]
      } yield new AppServer {

        val start: Task[Nothing] =
          Server
            .serve(Apps.analytics)
            .provide(
              Server.live,
              ServerConfig.live(config),
              ZLayer.succeed(ingestService)
            )
      }
    }
}
