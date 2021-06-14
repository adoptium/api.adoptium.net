package net.adoptopenjdk.api.v3.models

import java.time.ZonedDateTime

abstract class DbStatsEntry<T>(
    val date: ZonedDateTime
) {
    abstract fun getMetric(): Long
    abstract fun getId(): T
}
