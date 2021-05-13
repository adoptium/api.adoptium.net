package net.adoptopenjdk.api.v3

import io.quarkus.jsonb.JsonbConfigCustomizer
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.DateTime
import java.lang.reflect.Type
import java.time.format.DateTimeFormatter
import javax.json.bind.Jsonb
import javax.json.bind.JsonbBuilder
import javax.json.bind.JsonbConfig
import javax.json.bind.serializer.JsonbSerializer
import javax.json.bind.serializer.SerializationContext
import javax.json.stream.JsonGenerator
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.ParamConverter
import javax.ws.rs.ext.ParamConverterProvider
import javax.ws.rs.ext.Provider

@Provider
class JsonSerializerCustomizer : JsonbConfigCustomizer {
    override fun customize(config: JsonbConfig) {
        config.withFormatting(true)
    }
}

@Provider
class JsonSerialiserConfig : ContextResolver<Jsonb> {

    class DateTimeSerializer : JsonbSerializer<DateTime> {
        override fun serialize(dateTime: DateTime?, jsonGenerator: JsonGenerator, p2: SerializationContext?) {
            if (dateTime != null) {
                jsonGenerator.write(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime.dateTime))
            } else {
                jsonGenerator.writeNull()
            }
        }
    }

    private val jsonb = JsonbBuilder.create(
        JsonbConfig()
            .withSerializers(DateTimeSerializer())
            .withFormatting(true)
    )

    override fun getContext(objectType: Class<*>?): Jsonb {
        return jsonb
    }
}

@Provider
class ArchitectureParamConverterProvider : ParamConverterProvider {
    class ArchitectureDeserializer : ParamConverter<Architecture> {
        override fun fromString(value: String?): Architecture? {
            if (value == null) return null
            return Architecture.getValue(value)
        }

        override fun toString(value: Architecture?): String? {
            return value?.name
        }
    }

    override fun <T> getConverter(rawType: Class<T>, genericType: Type?, annotations: Array<Annotation?>?): ParamConverter<T>? {
        return if (rawType.isAssignableFrom(Architecture::class.java)) {
            ArchitectureDeserializer() as ParamConverter<T>?
        } else null
    }
}
