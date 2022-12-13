package com.tapad.analytics.http

import com.tapad.analytics.http.HttpConfig.HttpPort

import zio.config.magnolia.Descriptor

object HttpConfig {
  final case class HttpPort(value: Int) extends AnyVal

  implicit val portDescriptor: Descriptor[HttpPort] =
    Descriptor[Int].transformOrFailLeft(p =>
      if (p > 0 && p < 65_535)
        Right(HttpPort(p))
      else Left(s"Wrong port value: $p")
    )(_.value)
}

case class HttpConfig(port: HttpPort)
