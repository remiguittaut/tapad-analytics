package com.tapad.analytics

import com.tapad.analytics.http.HttpConfig
import com.tapad.analytics.storage.InfluxConfig

import zio.config.magnolia.Descriptor.descriptor
import zio.config.typesafe.TypesafeConfigSource
import zio.config.{ ReadError, read }
import zio.{ ZIO, ZLayer }

object AppConfig {

  val live: ZLayer[Any, ReadError[String], AppConfig] =
    ZLayer.fromZIO(for {
      _ <- ZIO.log("Loading configuration")
      typesafeSource = TypesafeConfigSource.fromResourcePath
      conf <- read(descriptor[AppConfig].from(typesafeSource))
        .tapError(error => ZIO.logError(s"Could not load configuration") *> ZIO.debug(error.prettyPrint()))
      _ <- ZIO.log("Done loading config")
    } yield conf)
}

final case class AppConfig(influx: InfluxConfig, http: HttpConfig)
