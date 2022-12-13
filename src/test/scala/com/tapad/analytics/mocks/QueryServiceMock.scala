package com.tapad.analytics.mocks

import java.time.Instant

import com.tapad.analytics.model.QueryResult
import com.tapad.analytics.query.QueryService

import zio.{ Chunk, Random, Ref, Task, ULayer, ZLayer }

trait QueryServiceMock extends QueryService with Mock

object QueryServiceMock extends MockHelpers[QueryServiceMock] {
  case class QueryForTimestamp(in: Instant) extends Invocation[QueryForTimestamp, Instant]

  val compose: ULayer[QueryServiceMock] =
    ZLayer.fromZIO {
      Ref
        .make(Chunk.empty[Invocation[_, _]])
        .map(ref =>
          new QueryServiceMock {
            override def queryForTimestamp(time: Instant): Task[QueryResult] =
              invocationRef.update(_ :+ QueryForTimestamp(time)) *>
                (Random.nextIntBounded(100_000) <*>
                  Random.nextIntBetween(100_000, 1_000_000) <*>
                  Random.nextIntBetween(100_000, 1_000_000)).map((QueryResult.apply _).tupled)

            override protected val invocationRef: Ref[Chunk[Invocation[_, _]]] = ref
          }
        )
    }
}
