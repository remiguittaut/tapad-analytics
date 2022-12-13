package com.tapad.analytics

import zio._
import zio.logging.{ LogFormat, consoleJson }

object Startup extends ZIOAppDefault {

  // TODO: Improve format
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJson(LogFormat.default)

  override def run: UIO[ExitCode] = ZIO.scoped(App.boot).exitCode
}
