package com.tapad.analytics.model

case class QueryResult(uniqueUsers: Int, clicks: Int, impressions: Int) {

  val asText: String =
    s"""unique_users,$uniqueUsers
       |clicks,$clicks
       |impressions,$impressions""".stripMargin
}

object QueryResult {
  val empty: QueryResult = QueryResult(0, 0, 0)
}
