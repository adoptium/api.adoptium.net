package net.adoptium.marketplace.dataSources.persitence.mongo

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.id.jackson.IdJacksonModule
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.util.KMongoConfiguration
import org.slf4j.LoggerFactory
import javax.inject.Singleton

interface MongoClient {
    fun getDatabase(): CoroutineDatabase
}

@Singleton
class MongoClientImpl : MongoClient {
    private var db: CoroutineDatabase? = null

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private const val DEFAULT_DBNAME = "api-data"
        private const val DEFAULT_HOST = "localhost"
        private const val DEFAULT_PORT = "27017"
        private const val DEFAULT_SERVER_SELECTION_TIMEOUT_MILLIS = "100"

        fun createConnectionString(
            dbName: String,
            username: String? = null,
            password: String? = null,
            host: String? = DEFAULT_HOST,
            port: String? = DEFAULT_PORT,
            serverSelectionTimeoutMills: String? = DEFAULT_SERVER_SELECTION_TIMEOUT_MILLIS
        ): String {
            val hostNonNull = host ?: DEFAULT_HOST
            val portNonNull = port ?: DEFAULT_PORT
            val serverSelectionTimeoutMillsNonNull = serverSelectionTimeoutMills ?: DEFAULT_SERVER_SELECTION_TIMEOUT_MILLIS

            val usernamePassword = if (username != null && password != null) {
                "$username:$password@"
            } else {
                ""
            }

            val server = "$hostNonNull:$portNonNull"

            return System.getProperty("MONGODB_TEST_CONNECTION_STRING")?.plus("/$dbName")
                ?: if (username != null && password != null) {
                    LOGGER.info("Connecting to mongodb://$username:a-password@$server/$dbName")
                    "mongodb://$usernamePassword$server/$dbName"
                } else {
                    val developmentConnectionString = "mongodb://$usernamePassword$server/?serverSelectionTimeoutMS=$serverSelectionTimeoutMillsNonNull"
                    LOGGER.info("Using development connection string - $developmentConnectionString")
                    developmentConnectionString
                }
        }
    }

    fun createDb(): CoroutineDatabase {
        val dbName = System.getenv("MONGODB_DBNAME") ?: DEFAULT_DBNAME
        val connectionString = createConnectionString(
            dbName,
            username = System.getenv("MONGODB_USER"),
            password = System.getenv("MONGODB_PASSWORD"),
            host = System.getenv("MONGODB_HOST"),
            port = System.getenv("MONGODB_PORT"),
            serverSelectionTimeoutMills = System.getenv("MONGODB_SERVER_SELECTION_TIMEOUT_MILLIS")
        )

        var settingsBuilder = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))

        val sslEnabled = System.getenv("MONGODB_SSL")?.toBoolean()
        if (sslEnabled == true) {
            settingsBuilder = settingsBuilder.applyToSslSettings { it.enabled(true).invalidHostNameAllowed(true) }
        }

        KMongoConfiguration.registerBsonModule(IdJacksonModule())
        KMongoConfiguration.registerBsonModule(Jdk8Module())
        KMongoConfiguration.registerBsonModule(JavaTimeModule())
        KMongoConfiguration.bsonMapper.disable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
        KMongoConfiguration.bsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        KMongoConfiguration.bsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

        val client = KMongo.createClient(settingsBuilder.build()).coroutine
        return client.getDatabase(dbName)
    }

    override fun getDatabase(): CoroutineDatabase {
        if (db == null) {
            db = createDb();
        }
        return db!!
    }
}
