package net.adoptium.api.v3.dataSources.persitence.mongo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MongoClientTest {

    @Test
    fun `default connection string`() {
        val connectionString = MongoClient.createConnectionString("api-data")
        assertEquals(connectionString, "mongodb://localhost:27017/?serverSelectionTimeoutMS=100")
    }

    @Test
    fun `default connection string with explicit server selection timeout`() {
        val connectionString = MongoClient.createConnectionString("api-data", serverSelectionTimeoutMills = "999")
        assertEquals(connectionString, "mongodb://localhost:27017/?serverSelectionTimeoutMS=999")
    }

    @Test
    fun `null arguments default`() {
        assertEquals(
            "mongodb://a:b@c:1/d",
            MongoClient.createConnectionString("d", "a", "b", "c", "1", "e")
        )

        assertEquals(
            "mongodb://c:1/?serverSelectionTimeoutMS=e",
            MongoClient.createConnectionString("d", null, "b", "c", "1", "e")
        )

        assertEquals(
            "mongodb://c:1/?serverSelectionTimeoutMS=e",
            MongoClient.createConnectionString("d", "a", null, "c", "1", "e")
        )

        assertEquals(
            "mongodb://a:b@localhost:1/d",
            MongoClient.createConnectionString("d", "a", "b", null, "1", "e")
        )

        assertEquals(
            "mongodb://a:b@c:27017/d",
            MongoClient.createConnectionString("d", "a", "b", "c", null, "e")
        )

        assertEquals(
            "mongodb://a:b@c:1/d",
            MongoClient.createConnectionString("d", "a", "b", "c", "1", null)
        )
    }

    @Test
    fun `override with test connection string`() {
        System.setProperty("MONGODB_TEST_CONNECTION_STRING", "mongodb://some-host:99999")
        val connectionString = MongoClient.createConnectionString("api-data")
        assertEquals(connectionString, "mongodb://some-host:99999")
    }

    @Test
    fun `fully specified connection string`() {
        val connectionString = MongoClient.createConnectionString(
            "api-data",
            "some-user",
            "some-password",
            "some-host",
            "12345"
        )
        assertEquals(connectionString, "mongodb://some-user:some-password@some-host:12345/api-data")
    }
}
