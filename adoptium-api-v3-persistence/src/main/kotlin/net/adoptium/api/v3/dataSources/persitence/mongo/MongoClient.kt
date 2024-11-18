package net.adoptium.api.v3.dataSources.persitence.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import jakarta.enterprise.context.ApplicationScoped
import net.adoptium.api.v3.dataSources.persitence.mongo.codecs.JacksonCodecProvider
import net.adoptium.api.v3.dataSources.persitence.mongo.codecs.ZonedDateTimeCodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.slf4j.LoggerFactory


@ApplicationScoped
open class MongoClient {
    private val database: MongoDatabase
    private val client: com.mongodb.kotlin.client.coroutine.MongoClient

    // required as injection objects to the final field
    open fun getDatabase() = database

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

            return System.getProperty("MONGODB_TEST_CONNECTION_STRING")
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

    init {
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
            .codecRegistry(CodecRegistries.fromProviders(
                MongoClientSettings.getDefaultCodecRegistry(),
                ZonedDateTimeCodecProvider(),
                JacksonCodecProvider()
            )
            )
            .applyConnectionString(ConnectionString(connectionString))
        val sslEnabled = System.getenv("MONGODB_SSL")?.toBoolean()
        if (sslEnabled == true) {
            val checkMongoHostName = System.getenv("DISABLE_MONGO_HOST_CHECK")?.toBoolean() ?: false

            settingsBuilder = settingsBuilder.applyToSslSettings { it.enabled(true).invalidHostNameAllowed(checkMongoHostName) }
        }
        client = com.mongodb.kotlin.client.coroutine.MongoClient.create(settingsBuilder.build())
        database = client.getDatabase(dbName)
    }

}
