package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptopenjdk.api.v3.models.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

class DateTimeMigrationTest : MongoTest() {

    class HasZonedDateTime(val zdt: ZonedDateTime)

    class HasDateTime(val zdt: DateTime)

    @Test
    fun `can serialize as zdt and deserialize as DateTime`() {
        val hzdt = HasZonedDateTime(TimeSource.now())
        val serialized = JsonMapper.mapper.writeValueAsString(hzdt)
        val deserialize = JsonMapper.mapper.readValue(serialized, HasDateTime::class.java)
        assertEquals(deserialize.zdt.dateTime, hzdt.zdt)
    }

    @Test
    fun `conversion provides milli resolution`(mongoClient: MongoClient) {
        val collectionName = UUID.randomUUID().toString()
        runBlocking {
            try {
                val date = TimeSource
                    .now()
                    .toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC)
                    .minus(1, TimeUnit.MILLISECONDS.toChronoUnit())

                val hzdt = HasZonedDateTime(date)
                val client1 = mongoClient.database.getCollection<HasZonedDateTime>(collectionName)
                client1.insertOne(hzdt)

                val hzdt2 = client1.findOne()
                assertEquals(hzdt.zdt, hzdt2?.zdt)

                val client2 = mongoClient.database.getCollection<HasDateTime>(collectionName)
                val fromDb = client2.findOne("{}")
                assertEquals(hzdt.zdt, fromDb?.zdt?.dateTime)
            } finally {
                mongoClient.database.dropCollection(collectionName)
            }
        }
    }

    @Test
    fun `writes datetime as string`(mongoClient: MongoClient) {
        val hzdt = HasDateTime(DateTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneOffset.UTC)))
        val serialized = JsonMapper.mapper.writeValueAsString(hzdt)
        assertEquals("{\"zdt\":\"1970-01-01T00:00:00Z\"}", serialized)
    }

    @Test
    fun `read old value from db works`(mongoClient: MongoClient) {
        val collectionName = UUID.randomUUID().toString()
        runBlocking {
            try {
                val hzdt = HasZonedDateTime(TimeSource.now())
                val client1 = mongoClient.database.getCollection<HasZonedDateTime>(collectionName)
                client1.insertOne(hzdt)

                val hzdt2 = client1.findOne()
                assertEquals(hzdt.zdt, hzdt2?.zdt)

                val client2 = mongoClient.database.getCollection<HasDateTime>(collectionName)
                val fromDb = client2.findOne("{}")
                assertEquals(hzdt.zdt, fromDb?.zdt?.dateTime)

                client2.deleteMany()
                client2.insertOne(fromDb!!)
                val fromDb2 = client2.findOne("{}")
                assertEquals(fromDb.zdt.dateTime, fromDb2?.zdt?.dateTime)
            } finally {
                mongoClient.database.dropCollection(collectionName)
            }
        }
    }
}
