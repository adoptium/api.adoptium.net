package net.adoptium.marketplace.server.frontend

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.module.SimpleSerializers
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import io.quarkus.jackson.ObjectMapperCustomizer
import net.adoptium.marketplace.dataSources.TimeSource
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.ws.rs.ext.Provider

/*
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
*/

@Provider
class JsonSerializerCustomizer : ObjectMapperCustomizer {
    class DateSerializer : StdSerializer<Date>(Date::class.java) {
        override fun serialize(dateTime: Date?, p1: JsonGenerator, p2: SerializerProvider) {
            if (dateTime != null) {
                val dt = ZonedDateTime.ofInstant(dateTime.toInstant(), TimeSource.ZONE)
                p1.writeString(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(dt))
            } else {
                p1.writeNull()
            }
        }
    }

    class OptionalSerializer : StdSerializer<Optional<*>>(Optional::class.java) {
        override fun serialize(optional: Optional<*>?, p1: JsonGenerator, p2: SerializerProvider?) {
            if (optional != null && optional.isPresent && optional.get() != null) {
                p1.writeObject(optional.get())
            }
        }
    }

    class CustomSerializersModule : SimpleModule() {
        override fun setupModule(context: SetupContext) {
            context.addSerializers(
                SimpleSerializers(
                    listOf(
                        DateSerializer(),
                        OptionalSerializer()
                    )
                )
            )
        }
    }

    override fun customize(objectMapper: ObjectMapper) {
        objectMapper
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(CustomSerializersModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
    }
}
