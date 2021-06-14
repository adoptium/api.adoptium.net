package net.adoptopenjdk.api

import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import net.adoptopenjdk.api.v3.dataSources.APIDataStoreImpl
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory
import kotlin.random.Random

@EnableAutoWeld
@AddPackages(value = [APIDataStoreImpl::class])
abstract class MongoTest {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        private var mongodExecutable: MongodExecutable? = null

        @JvmStatic
        @BeforeAll
        fun startDb() {
            System.setProperty("GITHUB_TOKEN", "stub-token")
            startFongo()
        }

        @JvmStatic
        fun startFongo() {
            val starter = MongodStarter.getDefaultInstance()

            val bindIp = "localhost"
            val port = Random.nextInt(10000, 16000)
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

        @JvmStatic
        @AfterAll
        fun closeMongo() {
            mongodExecutable!!.stop()
        }
    }
}
