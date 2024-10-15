package net.adoptium.api.v3.dataSources.persitence.mongo.codecs

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.adoptium.api.v3.TimeSource
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ZonedDateTimeCodecs {
    class ZonedDateTimeDeserializer : JsonDeserializer<ZonedDateTime>() {
        override fun deserialize(jsonParser: JsonParser?, context: DeserializationContext?): ZonedDateTime {
            when (val value = jsonParser?.readValueAsTree<JsonNode>()) {
                is ObjectNode -> {
                    val datetime = DateTimeFormatter.ISO_INSTANT.parse(value.get("\$date").asText())
                    return Instant.from(datetime).atZone(TimeSource.ZONE)
                }

                is TextNode -> {
                    val datetime = DateTimeFormatter.ISO_INSTANT.parse(value.asText())
                    return Instant.from(datetime).atZone(TimeSource.ZONE)
                }

                is LongNode -> {
                    return ZonedDateTime.ofInstant(Instant.ofEpochMilli(value.asLong()), TimeSource.ZONE)
                }
            }

            throw IllegalArgumentException("Could not parse ZonedDateTime")
        }

    }

    class ZonedDateTimeSerializer : JsonSerializer<ZonedDateTime>() {
        override fun serialize(p0: ZonedDateTime?, p1: JsonGenerator?, p2: SerializerProvider?) {
            p1?.writeStartObject();
            p1?.writeStringField("\$date", p0?.format(DateTimeFormatter.ISO_INSTANT));
            p1?.writeEndObject();
        }

    }
}
