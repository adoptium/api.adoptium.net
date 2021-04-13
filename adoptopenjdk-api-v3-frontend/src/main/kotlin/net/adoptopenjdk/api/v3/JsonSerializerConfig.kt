package net.adoptopenjdk.api.v3

import io.quarkus.jsonb.JsonbConfigCustomizer
import net.adoptopenjdk.api.v3.models.DateTime
import java.time.format.DateTimeFormatter
import javax.inject.Singleton
import javax.json.bind.Jsonb
import javax.json.bind.JsonbBuilder
import javax.json.bind.JsonbConfig
import javax.json.bind.serializer.JsonbSerializer
import javax.json.bind.serializer.SerializationContext
import javax.json.stream.JsonGenerator
import javax.ws.rs.ext.ContextResolver

@Singleton
class JsonSerializerConfig : ContextResolver<Jsonb> {
    class DateTimeSerializer : JsonbSerializer<DateTime> {
        override fun serialize(dateTime: DateTime?, jsonGenerator: JsonGenerator, p2: SerializationContext?) {
            if (dateTime != null) {
                jsonGenerator.write(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime.dateTime))
            } else {
                jsonGenerator.writeNull()
            }
        }

    }

    private val jsonb = JsonbBuilder.create(JsonbConfig()
        .withSerializers(DateTimeSerializer())
        .withFormatting(true)
    )

    override fun getContext(objectType: Class<*>?): Jsonb {
        return jsonb
    }
}
