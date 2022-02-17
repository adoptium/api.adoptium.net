package net.adoptium.marketplace.server.updater

import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.runtime.Startup
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.adoptium.marketplace.client.MarketplaceClient
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.dataSources.ModelComparators
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.Vendor
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.inject.Singleton
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application
import kotlin.concurrent.timerTask

@UnlessBuildProfile("test")
@ApplicationScoped
@ApplicationPath("/")
@Startup
class AdoptiumMarketplaceUpdaterApp : Application()

@UnlessBuildProfile("test")
@Singleton
@Startup
class KickOffUpdate @Inject constructor(
    adoptiumMarketplaceUpdater: AdoptiumMarketplaceUpdater
) {
    init {
        adoptiumMarketplaceUpdater.scheduleUpdates()
    }
}

interface Updater {
    suspend fun update(vendor: Vendor): ReleaseList
    fun scheduleUpdates()
}

@Singleton
class AdoptiumMarketplaceUpdater @Inject constructor(
    private val apiDataStore: APIDataStore,
    private val vendorList: VendorList
) : Updater {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val clients: Map<Vendor, MarketplaceClient>

    private val mutex = Mutex()

    init {
        clients = buildClientMap()
    }

    private fun buildClientMap(): Map<Vendor, MarketplaceClient> {
        return vendorList.getVendorInfo()
            .map {
                return@map it.key to MarketplaceClient.build(it.value.repoUrl, it.value.getKey())!!
            }.toMap()
    }

    override fun scheduleUpdates() {
        val executor = Executors.newScheduledThreadPool(2)

        executor.scheduleWithFixedDelay(
            timerTask {
                try {
                    runUpdate()
                } catch (e: Throwable) {
                    LOGGER.error("Caught exception", e)
                }
            }, 0, 1, TimeUnit.HOURS
        )
    }

    private fun runUpdate() {
        runBlocking {
            clients
                .keys
                .forEach { vendor ->
                    val newReleases = update(vendor)

                    newReleases
                        .releases
                        .forEach { LOGGER.info("Added release: ${it.release_name}") }
                }
        }
    }

    override suspend fun update(vendor: Vendor): ReleaseList {
        mutex.withLock {
            val releasesBefore = apiDataStore.getReleases(vendor).getAllReleases()

            val releases = clients[vendor]?.readRepositoryData() ?: ReleaseList(emptyList())

            val newReleases = apiDataStore.getReleases(vendor).writeReleases(releases)

            logInfoAboutUpdate(vendor, newReleases, releasesBefore)

            return newReleases
        }
    }

    private suspend fun logInfoAboutUpdate(vendor: Vendor, newReleases: ReleaseList, releasesBeforeUpdate: ReleaseList) {
        val releasesAfter = apiDataStore.getReleases(vendor).getAllReleases()

        newReleases
            .releases
            .forEach { LOGGER.info("New release added $vendor ${it.release_name}") }

        releasesBeforeUpdate
            .releases
            .filter { release ->
                releasesAfter
                    .releases
                    .none { ModelComparators.RELEASE.compare(release, it) == 0 }
            }
            .forEach {
                LOGGER.error("Release disappeared or has mutated, contact $vendor to find out why ${it.release_name} ${it.release_link}")
            }
    }
}
