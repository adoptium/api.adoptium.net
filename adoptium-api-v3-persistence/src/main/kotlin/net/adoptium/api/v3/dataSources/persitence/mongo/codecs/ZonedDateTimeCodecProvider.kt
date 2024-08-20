package net.adoptium.api.v3.dataSources.persitence.mongo.codecs

import net.adoptium.api.v3.TimeSource
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import java.time.Instant
import java.time.ZonedDateTime

class ZonedDateTimeCodecProvider : CodecProvider {
    override fun <T> get(type: Class<T>?, registry: CodecRegistry?): Codec<T>? {
        if (type == ZonedDateTime::class.java) {
            return ZonedDateTimeCodec() as Codec<T>
        }
        return null
    }
}

class ZonedDateTimeCodec : Codec<ZonedDateTime> {
    override fun encode(writer: BsonWriter?, value: ZonedDateTime?, encoder: EncoderContext?) {
        if (value == null) {
            writer?.writeNull()
        } else {
            writer?.writeDateTime(value.withZoneSameInstant(TimeSource.ZONE).toInstant().toEpochMilli())
        }
    }

    override fun getEncoderClass(): Class<ZonedDateTime> {
        return ZonedDateTime::class.java
    }

    override fun decode(reader: BsonReader?, decoderContext: DecoderContext?): ZonedDateTime? {
        val date = reader?.readDateTime()
        return if (date == null) {
            null
        } else {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(date), TimeSource.ZONE)
        }
    }
}
