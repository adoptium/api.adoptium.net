package net.adoptium.api

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.GitHubDownloadStatsDbEntry
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

data class HasZonedDateTime(
    val zdt: ZonedDateTime)

data class HasDateTime(
    val zdt: DateTime)

class DateTimeMigrationTest : MongoTest() {


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
                val client1 = mongoClient.getDatabase().getCollection<HasZonedDateTime>(collectionName)
                client1.insertOne(hzdt)

                val hzdt2 = client1.find().firstOrNull()
                assertEquals(hzdt.zdt, hzdt2?.zdt)

                val client2 = mongoClient.getDatabase().getCollection<HasZonedDateTime>(collectionName)
                val fromDb = client2.find().firstOrNull()
                assertEquals(hzdt.zdt, fromDb?.zdt)
            } finally {
                mongoClient.getDatabase().getCollection<Any>(collectionName).drop()
            }
        }
    }

    @Test
    fun `github stats`(mongoClient: MongoClient) {
        runBlocking {
            val collectionName = UUID.randomUUID().toString()
            val client1 = mongoClient.getDatabase().getCollection<GitHubDownloadStatsDbEntry>(collectionName)

            client1.insertOne(
                GitHubDownloadStatsDbEntry(
                    TimeSource.now(),
                    1,
                    mapOf(),
                    1
                )
            )

            client1.find().firstOrNull()?.let {
                assertEquals(1, it.downloads)
            }
        }

    }

    @Test
    fun `writes datetime as string`() {
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
                val client1 = mongoClient.getDatabase().getCollection<HasZonedDateTime>(collectionName)
                client1.insertOne(hzdt)

                val hzdt2 = client1.find().firstOrNull()
                assertEquals(hzdt.zdt, hzdt2?.zdt)

                val client2 = mongoClient.getDatabase().getCollection<HasZonedDateTime>(collectionName)
                val fromDb = client2.find(Document.parse("{}")).firstOrNull()
                assertEquals(hzdt.zdt, fromDb?.zdt)

                client2.deleteMany(Document.parse("{}"))
                client2.insertOne(fromDb!!)
                val fromDb2 = client2.find(Document.parse("{}")).firstOrNull()
                assertEquals(fromDb.zdt, fromDb2?.zdt)
            } finally {
                mongoClient.getDatabase().getCollection<Any>(collectionName).drop()
            }
        }
    }
}
