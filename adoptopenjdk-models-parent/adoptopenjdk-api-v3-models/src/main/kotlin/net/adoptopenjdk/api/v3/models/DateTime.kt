package net.adoptopenjdk.api.v3.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalQuery
import java.util.Date
import java.util.concurrent.TimeUnit

@JsonSerialize(using = DateTimeSerializer::class)
@JsonDeserialize(using = DateTimeDeSerializer::class)
@Schema(
    type = SchemaType.STRING,
    description = "<p>Date/time. When only a date is given the time is set to the end of the given day. <ul> <li>2020-01-21</li> <li>2020-01-21T10:15:30</li> <li>20200121</li> <li>2020-12-21T10:15:30Z</li> <li>2020-12-21+01:00</li> </ul></p>"
)
class DateTime {

    @Schema(hidden = true)
    val dateTime: ZonedDateTime

    @Suppress("unused")
    constructor(time: Number) {
        this.dateTime = Instant.ofEpochSecond(time.toLong()).atZone(ZoneOffset.of("Z"))
    }

    @JsonCreator
    constructor(
        @JsonProperty("dateTime")
        dateTime: ZonedDateTime
    ) {
        this.dateTime = dateTime.withZoneSameInstant(ZoneOffset.of("Z"))
    }

    @JsonCreator
    constructor(
        @JsonProperty("dateTime")
        dateTime: Date
    ) {
        this.dateTime = ZonedDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.of("Z"))
    }

    @Throws(UnableToParseDateException::class)
    constructor(time: String) {
        this.dateTime = parseDate(time)
    }

    override fun toString(): String {
        return DateTimeFormatter.ISO_INSTANT.format(dateTime)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DateTime

        if (dateTime != other.dateTime) return false

        return true
    }

    override fun hashCode(): Int {
        return dateTime.hashCode()
    }

    companion object {
        @Throws(UnableToParseDateException::class)
        fun parseDate(rawDate: String): ZonedDateTime {

            val zoneOffset = ZoneOffset.of("Z")
            try {
                return LocalDate.parse(rawDate, DateTimeFormatter.ISO_DATE)
                    .plusDays(1)
                    .atStartOfDay(zoneOffset)
                    .minus(1, TimeUnit.MILLISECONDS.toChronoUnit())
            } catch (e: DateTimeParseException) {
                // NOP
            }

            try {
                val number = rawDate.toLong()
                if (rawDate.length == 10) {
                    return Instant.ofEpochSecond(number).atZone(zoneOffset)
                } else if (rawDate.length == 13) {
                    return Instant.ofEpochMilli(number).atZone(zoneOffset)
                }
            } catch (e: NumberFormatException) {
                // NOP
            }

            try {
                val parsedDate = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                    rawDate,
                    TemporalQuery { p0 -> ZonedDateTime.from(p0) },
                    TemporalQuery { p0 -> OffsetDateTime.from(p0) },
                    TemporalQuery { p0 -> LocalDateTime.from(p0) }
                )

                return when (parsedDate) {
                    is LocalDateTime -> parsedDate.atZone(zoneOffset)
                    is OffsetDateTime -> parsedDate.atZoneSameInstant(zoneOffset)
                    is ZonedDateTime -> parsedDate
                    else -> throw UnableToParseDateException("Failed to parse date: $rawDate")
                }
            } catch (e: DateTimeParseException) {
                // NOP
            }

            try {
                val date = LocalDate.parse(rawDate, DateTimeFormatter.BASIC_ISO_DATE)
                return date
                    .plusDays(1)
                    .atStartOfDay(zoneOffset)
                    .minus(1, TimeUnit.MILLISECONDS.toChronoUnit())
            } catch (e: DateTimeParseException) {
                throw UnableToParseDateException("Failed to parse date: $rawDate")
            }
        }

        class UnableToParseDateException(msg: String) : Exception(msg)
    }
}

class DateTimeSerializer : JsonSerializer<DateTime>() {
    override fun serialize(dateTime: DateTime?, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider?) {
        if (dateTime != null) {
            jsonGenerator.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime.dateTime))
        } else {
            jsonGenerator.writeNull()
        }
    }
}

class DateTimeDeSerializer : JsonDeserializer<DateTime>() {
    override fun deserialize(parser: JsonParser?, context: DeserializationContext?): DateTime {
        if (parser!!.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return DateTime(InstantDeserializer.ZONED_DATE_TIME.deserialize(parser, context))
        } else if (parser.currentToken() == JsonToken.VALUE_EMBEDDED_OBJECT) {
            return when (val obj = parser.embeddedObject) {
                is Date -> {
                    val date = parser.readValueAs(Date::class.java)
                    DateTime(date.toInstant().atZone(ZoneOffset.of("Z")))
                }
                is DateTime -> obj
                is ZonedDateTime -> DateTime(obj)
                else -> throw RuntimeException("Unknown type " + obj::class.java)
            }
        } else if (parser.currentToken() == JsonToken.START_OBJECT) {
            val fieldName = parser.nextFieldName()
            parser.nextToken()
            if (fieldName == "dateTime") {
                val dt = DateTime(parser.readValueAs(ZonedDateTime::class.java))
                // Pop end token
                parser.nextValue()
                return dt
            } else {
                throw RuntimeException("Unknown field")
            }
        } else if (parser.currentToken() == JsonToken.VALUE_STRING) {
            val field = parser.text
            if (field != null) {
                return DateTime(field)
            } else {
                throw RuntimeException("Null DateTime detected")
            }
        } else {
            throw RuntimeException("Unknown token type " + parser.currentToken())
        }
    }
}
