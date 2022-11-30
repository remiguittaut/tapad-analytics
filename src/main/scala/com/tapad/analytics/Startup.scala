package com.tapad.analytics

import zio._

object Startup extends ZIOAppDefault {
  override def run: UIO[ExitCode] = Console.printLine("Initial app").exitCode
}
