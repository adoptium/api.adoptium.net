package net.adoptium.api

import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.embed.process.runtime.Network
import de.flapdoodle.reverse.transitions.Start
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory

@EnableAutoWeld
@AddPackages(value = [APIDataStoreImpl::class])
abstract class MongoTest {

    companion object {
        private var mongodExecutable: RunningMongodProcess? = null

        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        @JvmStatic
        @BeforeAll
        fun startDb() {
            System.setProperty("GITHUB_TOKEN", "stub-token")
            startFongo()
        }

        @JvmStatic
        fun startFongo() {
            val bindIp = "localhost"
            val net = Net.of("localhost",
                Network.freeServerPort(Network.getLocalHost()),
                Network.localhostIsIPv6()
            )

            val mongodbTestConnectionString = "mongodb://$bindIp:${net.port}"
            LOGGER.info("Mongo test connection string - $mongodbTestConnectionString")
            System.setProperty("MONGODB_TEST_CONNECTION_STRING", mongodbTestConnectionString)

            mongodExecutable = Mongod.instance().withNet(Start.to(Net::class.java).initializedWith(net)).start(Version.V4_4_18).current()

            LOGGER.info("FMongo started")
        }

        @JvmStatic
        @AfterAll
        fun closeMongo() {
            mongodExecutable!!.stop()
        }
    }
}
