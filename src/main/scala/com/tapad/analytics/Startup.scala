package com.tapad.analytics

import com.tapad.analytics.http.AppServer
import zio._
import zio.logging.{ LogFormat, consoleJson }

object Startup extends ZIOAppDefault {

  // TODO: Improve format
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(LogFormat.default)

  override def run: UIO[ExitCode] = {
    for {
      _ <- ZIO.log("Serving Api")
      _ <- AppServer.start
    } yield ()
  }.exitCode
}
