package net.adoptium.api.v3.dataSources.persitence.mongo.codecs

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.adoptium.api.v3.TimeSource
import java.time.ZonedDateTime

class ZonedDateTimeDeserializer : JsonDeserializer<ZonedDateTime>() {
    override fun deserialize(jsonParser: JsonParser?, context: DeserializationContext?): ZonedDateTime {
        when (val value = jsonParser?.readValueAsTree<JsonNode>()) {
            is ObjectNode -> {
                return ZonedDateTime.parse(value.get("\$date").asText())
            }

            is TextNode -> {
                return ZonedDateTime.parse(value.asText())
            }

            is LongNode -> {
                return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(value.asLong()), TimeSource.ZONE)
            }
        }

        throw IllegalArgumentException("Could not parse ZonedDateTime")
    }

}
