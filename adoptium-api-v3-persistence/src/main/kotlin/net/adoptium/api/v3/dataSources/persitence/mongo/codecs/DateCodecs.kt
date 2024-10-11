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
import java.time.format.DateTimeFormatter
import java.util.*

object DateCodecs {
    class DateSerializer : JsonSerializer<Date>() {
        override fun serialize(p0: Date?, p1: JsonGenerator?, p2: SerializerProvider?) {
            p1?.writeStartObject();
            p1?.writeStringField(
                "\$date",
                p0?.toInstant()?.atZone(TimeSource.ZONE)?.format(DateTimeFormatter.ISO_INSTANT)
            );
            p1?.writeEndObject();
        }
    }

    class DateDeserializer : JsonDeserializer<Date>() {
        override fun deserialize(jsonParser: JsonParser?, context: DeserializationContext?): Date {
            when (val value = jsonParser?.readValueAsTree<JsonNode>()) {
                is ObjectNode -> {
                    val datetime = DateTimeFormatter.ISO_INSTANT.parse(value.get("\$date").asText())
                    return Date(Instant.from(datetime).toEpochMilli())
                }

                is TextNode -> {
                    val datetime = DateTimeFormatter.ISO_INSTANT.parse(value.asText())
                    return Date(Instant.from(datetime).toEpochMilli())
                }

                is LongNode -> {
                    return Date(value.asLong())
                }
            }

            throw IllegalArgumentException("Could not parse ZonedDateTime")
        }

    }

}
