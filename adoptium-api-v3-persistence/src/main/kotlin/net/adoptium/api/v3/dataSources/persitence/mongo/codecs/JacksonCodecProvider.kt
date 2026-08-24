package net.adoptium.api.v3.dataSources.persitence.mongo.codecs

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.RawBsonDocument
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import java.io.IOException
import java.io.UncheckedIOException
import java.time.ZonedDateTime
import java.util.*

class JacksonCodecProvider : CodecProvider {
    companion object {
        private val objectMapper: ObjectMapper = jacksonMapperBuilder {
            disable(KotlinFeature.StrictNullChecks)
        }
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(object : SimpleModule() {
                init {
                    addDeserializer(ZonedDateTime::class.java, ZonedDateTimeCodecs.ZonedDateTimeDeserializer())
                    addSerializer(ZonedDateTime::class.java, ZonedDateTimeCodecs.ZonedDateTimeSerializer())
                    addDeserializer(Date::class.java, DateCodecs.DateDeserializer())
                    addSerializer(Date::class.java, DateCodecs.DateSerializer())
                }
            })
            .build()
    }

    override fun <T> get(type: Class<T>, registry: CodecRegistry): Codec<T>? {
        if (type == RawBsonDocument::class.java) {
            return null
        }
        return JacksonCodec(objectMapper, registry, type)
    }
}

class JacksonCodec<T>(private val objectMapper: ObjectMapper, private val registry: CodecRegistry, val type: Class<T>) :
    Codec<T> {

    private var rawBsonDocumentCodec: Codec<RawBsonDocument> = registry.get(RawBsonDocument::class.java)

    override fun encode(bsonWriter: BsonWriter?, value: T, encoderContext: EncoderContext?) {
        val doc = RawBsonDocument.parse(objectMapper.writeValueAsString(value))

        rawBsonDocumentCodec.encode(bsonWriter, doc, encoderContext)
    }

    override fun getEncoderClass(): Class<T> {
        return type
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): T {
        try {
            val codec = registry.get(RawBsonDocument::class.java)
            val document: RawBsonDocument? = codec?.decode(reader, decoderContext)
            val json = document?.toJson()
            return objectMapper.readValue(json, type)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }
}
