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
import java.time.format.DateTimeFormatter
import java.util.*

object DateCodecs {
    class DateSerializer : ValueSerializer<Date>() {
        override fun serialize(p0: Date, p1: JsonGenerator, p2: SerializationContext) {
            p1.writeStartObject()
            p1.writeStringProperty(
                $$"$date",
                p0.toInstant().atZone(TimeSource.ZONE).format(DateTimeFormatter.ISO_INSTANT)
            )
            p1.writeEndObject()
        }
    }

    class DateDeserializer : ValueDeserializer<Date>() {
        override fun deserialize(jsonParser: JsonParser, context: DeserializationContext): Date {
            when (val value = jsonParser.readValueAsTree<JsonNode>()) {
                is ObjectNode -> {
                    val datetime = DateTimeFormatter.ISO_INSTANT.parse(value.get($$"$date").asText())
                    return Date(Instant.from(datetime).toEpochMilli())
                }

                is StringNode -> {
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
