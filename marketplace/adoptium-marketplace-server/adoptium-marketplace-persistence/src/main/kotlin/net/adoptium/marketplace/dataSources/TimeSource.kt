package net.adoptium.marketplace.dataSources

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object TimeSource {
    val ZONE: ZoneId = ZoneId.of("Z")
    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS)
    fun date(): LocalDate = LocalDate.now(ZONE)
}
