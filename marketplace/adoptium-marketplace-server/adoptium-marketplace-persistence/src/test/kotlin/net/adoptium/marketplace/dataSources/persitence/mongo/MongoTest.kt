package net.adoptium.marketplace.dataSources.persitence.mongo

import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import kotlin.random.Random

class MongoTest : BeforeAllCallback, AfterAllCallback {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        private var mongodExecutable: MongodExecutable? = null

        @JvmStatic
        fun startFongo() {
            startFongo(Random.nextInt(10000, 16000))
        }

        fun startFongo(port: Int) {
            val starter = MongodStarter.getDefaultInstance()

            val bindIp = "localhost"
            val mongodConfig = MongodConfigBuilder()
                .version(Version.V4_0_2)
                .net(Net(bindIp, port, Network.localhostIsIPv6()))
                .build()

            val mongodbTestConnectionString = "mongodb://$bindIp:$port"
            LOGGER.info("Mongo test connection string - $mongodbTestConnectionString")
            System.setProperty("MONGODB_TEST_CONNECTION_STRING", mongodbTestConnectionString)

            mongodExecutable = starter.prepare(mongodConfig)
            mongodExecutable!!.start()

            LOGGER.info("FMongo started")
        }
    }

    override fun beforeAll(p0: ExtensionContext?) {
        System.setProperty("GITHUB_TOKEN", "stub-token")
        startFongo()
    }

    override fun afterAll(p0: ExtensionContext?) {
        mongodExecutable!!.stop()
    }
}
