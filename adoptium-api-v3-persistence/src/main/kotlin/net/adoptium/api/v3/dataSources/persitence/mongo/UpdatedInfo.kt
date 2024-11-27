package net.adoptium.api.v3.dataSources.persitence.mongo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdatedInfo(
    val time: ZonedDateTime,
    val checksum: String,
    val hashCode: Int,
    val lastModified: Date? = Date.from(time.toInstant()),
    val lastModifiedFormatted: String? = lastModified
        ?.toInstant()
        ?.atZone(ZoneId.of("GMT"))
        ?.format(DateTimeFormatter.RFC_1123_DATE_TIME)) {

    override fun toString(): String {
        return "$time $checksum $hashCode"
    }
}
