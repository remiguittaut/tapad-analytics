package com.tapad.analytics.model

import java.time.Instant

case class MetricEvent(timestamp: Instant, user: String, metric: String)
