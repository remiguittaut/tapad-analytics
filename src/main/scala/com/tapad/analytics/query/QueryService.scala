package com.tapad.analytics.query

import java.time.Instant

import com.tapad.analytics.model.QueryResult
import com.tapad.analytics.storage.InfluxClient

import zio.macros.accessible
import zio.{ Task, URLayer, ZIO, ZLayer }

@accessible
trait QueryService {
  def queryForTimestamp(time: Instant): Task[QueryResult]
}

object QueryService {

  val live: URLayer[InfluxClient, QueryService] =
    ZLayer.fromZIO {
      ZIO
        .service[InfluxClient]
        .map(client =>
          new QueryService {
            override def queryForTimestamp(time: Instant): Task[QueryResult] = client.queryMetrics(time)
          }
        )
    }
}
