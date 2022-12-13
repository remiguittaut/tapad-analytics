package com.tapad.analytics.storage

import com.tapad.analytics.storage.InfluxConfig.{ InfluxBucket, InfluxEndpoint, InfluxOrg, InfluxToken }
import sttp.model.Uri

import zio.config._
import zio.config.magnolia.{ Descriptor, descriptor }

object InfluxConfig {
  final case class InfluxEndpoint(uri: Uri)    extends AnyVal
  final case class InfluxToken(value: String)  extends AnyVal
  final case class InfluxBucket(value: String) extends AnyVal
  final case class InfluxOrg(value: String)    extends AnyVal

  implicit val endpointDescriptor: Descriptor[InfluxEndpoint] =
    Descriptor[String].transformOrFailLeft(
      Uri.parse(_).map(InfluxEndpoint)
    )(_.toString)

  implicit val tokenDescriptor: Descriptor[InfluxToken] =
    Descriptor[String].transform(InfluxToken, _.value)

  implicit val bucketDescriptor: Descriptor[InfluxBucket] =
    Descriptor[String].transform(InfluxBucket, _.value)

  implicit val orgDescriptor: Descriptor[InfluxOrg] =
    Descriptor[String].transform(InfluxOrg, _.value)

  implicit val influxConfig: ConfigDescriptor[InfluxConfig] = descriptor[InfluxConfig]
}

final case class InfluxConfig(endpoint: InfluxEndpoint, token: InfluxToken, bucket: InfluxBucket, org: InfluxOrg)
