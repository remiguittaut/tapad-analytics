package com.tapad.analytics.http

import zio.Task
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http.{ Server, ServerConfig }

object AppServer {

  val start: Task[Unit] =
    Server
      .serve(Apps.analytics)
      .provide(
        ServerConfig.live(
          ServerConfig.default
            .port(8080)
            .maxThreads(8)
            .leakDetection(LeakDetectionLevel.PARANOID)
        ),
        Server.live
      )
}
