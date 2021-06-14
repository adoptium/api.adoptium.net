package net.adoptopenjdk.api.v3

import io.quarkus.jsonb.JsonbConfigCustomizer
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.DateTime
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.Vendor
import java.lang.reflect.Type
import java.time.format.DateTimeFormatter
import javax.json.bind.JsonbConfig
import javax.json.bind.serializer.JsonbSerializer
import javax.json.bind.serializer.SerializationContext
import javax.json.stream.JsonGenerator
import javax.ws.rs.ext.ParamConverter
import javax.ws.rs.ext.ParamConverterProvider
import javax.ws.rs.ext.Provider

@Provider
class JsonSerializerCustomizer : JsonbConfigCustomizer {

    class DateTimeSerializer : JsonbSerializer<DateTime> {
        override fun serialize(dateTime: DateTime?, jsonGenerator: JsonGenerator, p2: SerializationContext?) {
            if (dateTime != null) {
                jsonGenerator.write(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime.dateTime))
            } else {
                jsonGenerator.writeNull()
            }
        }
    }

    override fun customize(config: JsonbConfig) {
        config
            .withSerializers(DateTimeSerializer())
            .withFormatting(true)
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

/**
 * Use to double check that we will not accept a vendor that is not available for this ecosystem
 */
@Provider
class VendorParamConverterProvider : ParamConverterProvider {
    class VendorDeserializer : ParamConverter<Vendor> {
        override fun fromString(value: String?): Vendor? {
            if (value == null) return null
            return Vendor.getValue(value)
        }

        override fun toString(value: Vendor?): String? {
            return value?.name
        }
    }

    override fun <T> getConverter(rawType: Class<T>, genericType: Type?, annotations: Array<Annotation?>?): ParamConverter<T>? {
        return if (rawType.isAssignableFrom(Vendor::class.java)) {
            VendorDeserializer() as ParamConverter<T>?
        } else null
    }
}

/**
 * Use to double check that we will not accept a JvmImpl that is not available for this ecosystem
 */
@Provider
class JvmImplParamConverterProvider : ParamConverterProvider {
    class JvmImplDeserializer : ParamConverter<JvmImpl> {
        override fun fromString(value: String?): JvmImpl? {
            if (value == null) return null
            return JvmImpl.getValue(value)
        }

        override fun toString(value: JvmImpl?): String? {
            return value?.name
        }
    }

    override fun <T> getConverter(rawType: Class<T>, genericType: Type?, annotations: Array<Annotation?>?): ParamConverter<T>? {
        return if (rawType.isAssignableFrom(JvmImpl::class.java)) {
            JvmImplDeserializer() as ParamConverter<T>?
        } else null
    }
}
