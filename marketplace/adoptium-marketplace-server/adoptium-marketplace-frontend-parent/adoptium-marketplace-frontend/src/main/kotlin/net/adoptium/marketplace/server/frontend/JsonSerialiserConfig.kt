package net.adoptium.marketplace.server.frontend

import io.quarkus.jsonb.JsonbConfigCustomizer
import net.adoptium.marketplace.dataSources.TimeSource
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.json.bind.JsonbConfig
import javax.json.bind.serializer.JsonbSerializer
import javax.json.bind.serializer.SerializationContext
import javax.json.stream.JsonGenerator
import javax.ws.rs.ext.Provider


@Provider
class JsonSerializerCustomizer : JsonbConfigCustomizer {

    class DateSerializer : JsonbSerializer<Date> {
        override fun serialize(dateTime: Date?, jsonGenerator: JsonGenerator, p2: SerializationContext?) {
            if (dateTime != null) {
                val dt = ZonedDateTime.ofInstant(dateTime.toInstant(), TimeSource.ZONE)
                jsonGenerator.write(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(dt))
            } else {
                jsonGenerator.writeNull()
            }
        }
    }

    override fun customize(config: JsonbConfig) {
        config
            .withSerializers(DateSerializer())
            .withFormatting(true)
    }
}
