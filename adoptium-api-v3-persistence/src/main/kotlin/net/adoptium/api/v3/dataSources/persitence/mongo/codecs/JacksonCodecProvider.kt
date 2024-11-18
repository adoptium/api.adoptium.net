package net.adoptium.api.v3.dataSources.persitence.mongo.codecs

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
        private val objectMapper: ObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(JavaTimeModule())
            .registerModule(object : SimpleModule() {
                init {
                    addDeserializer(ZonedDateTime::class.java, ZonedDateTimeCodecs.ZonedDateTimeDeserializer())
                    addSerializer(ZonedDateTime::class.java, ZonedDateTimeCodecs.ZonedDateTimeSerializer())
                    addDeserializer(Date::class.java, DateCodecs.DateDeserializer())
                    addSerializer(Date::class.java, DateCodecs.DateSerializer())
                }
            })
    }

    override fun <T : Any?> get(type: Class<T>, registry: CodecRegistry): Codec<T>? {
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
