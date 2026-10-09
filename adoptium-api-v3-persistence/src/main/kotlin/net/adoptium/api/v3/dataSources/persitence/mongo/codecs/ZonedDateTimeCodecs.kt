package net.adoptium.api.v3.dataSources.persitence.mongo.codecs

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.node.LongNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode
import net.adoptium.api.v3.TimeSource
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ZonedDateTimeCodecs {
    class ZonedDateTimeDeserializer : ValueDeserializer<ZonedDateTime>() {
        override fun deserialize(jsonParser: JsonParser, context: DeserializationContext): ZonedDateTime {
            when (val value = jsonParser.readValueAsTree<JsonNode>()) {
                is ObjectNode -> {
                    val datetime = DateTimeFormatter.ISO_INSTANT.parse(value.get($$"$date").asText())
                    return Instant.from(datetime).atZone(TimeSource.ZONE)
                }

                is StringNode -> {
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

    class ZonedDateTimeSerializer : ValueSerializer<ZonedDateTime>() {
        override fun serialize(p0: ZonedDateTime, p1: JsonGenerator, p2: SerializationContext) {
            p1.writeStartObject();
            p1.writeStringProperty("\$date", p0.format(DateTimeFormatter.ISO_INSTANT));
            p1.writeEndObject();
        }

    }
}
