package net.adoptium.marketplace.server.updater

import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.runtime.Startup
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.adoptium.marketplace.client.FailedToPullDataException
import net.adoptium.marketplace.client.MarketplaceClient
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.dataSources.ModelComparators
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.ReleaseUpdateInfo
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.server.updater.routes.UpdateTrigger
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
/**
 * Root application for the updater project
 */
class AdoptiumMarketplaceUpdaterApp : Application()

@UnlessBuildProfile("test")
@Singleton
@Startup
/**
 * Init application tasks
 */
class KickOffUpdate @Inject constructor(
    adoptiumMarketplaceUpdater: AdoptiumMarketplaceUpdater
) {
    init {
        adoptiumMarketplaceUpdater.scheduleUpdates()
    }
}

interface Updater {
    suspend fun update(vendor: Vendor): ReleaseUpdateInfo
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

    private var UPDATE_PERIOD_IN_MIN: Long
    private val clients: Map<Vendor, MarketplaceClient>

    private val mutex = Mutex()

    init {
        clients = buildClientMap()
        UPDATE_PERIOD_IN_MIN = Integer.parseInt(System.getProperty("UPDATE_PERIOD_IN_MIN", "5")).toLong()
    }

    private fun buildClientMap(): Map<Vendor, MarketplaceClient> {
        return vendorList.getVendorInfo()
            .map {
                return@map it.key to MarketplaceClient.build(it.value.getUrl(), it.value.getSignatureType(), it.value.getKey())!!
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
            }, 0, UPDATE_PERIOD_IN_MIN, TimeUnit.MINUTES
        )
    }

    private fun runUpdate() {
        runBlocking {
            clients
                .keys
                .forEach { vendor ->
                    val newReleases = update(vendor)
                    logInfoAboutUpdate(vendor, newReleases)
                }
        }
    }

    override suspend fun update(vendor: Vendor): ReleaseUpdateInfo {
        mutex.withLock {
            return try {
                val releasesBefore = apiDataStore.getReleases(vendor).getAllReleases()
                val releases = clients[vendor]?.readRepositoryData() ?: ReleaseList(emptyList())

                val newReleases = apiDataStore.getReleases(vendor).writeReleases(releases)
                logInfoAboutUpdate(vendor, newReleases, releasesBefore)
                newReleases
            } catch (e: FailedToPullDataException) {
                LOGGER.error("Failed to update repo for $vendor. ", e)
                ReleaseUpdateInfo("Failed to update repo for $vendor. " + e.message)
            }
        }
    }

    private suspend fun logInfoAboutUpdate(vendor: Vendor, newReleases: ReleaseUpdateInfo, releasesBeforeUpdate: ReleaseList? = null) {
        if (newReleases.errorMessage != null) {
            LOGGER.error(newReleases.errorMessage);
            return
        }

        val releasesAfter = apiDataStore.getReleases(vendor).getAllReleases()

        LOGGER.info("Updated $vendor, found ${releasesAfter.releases.size} releases, ${newReleases.updated.releases.size} updated, ${newReleases.added.releases.size} added, ${newReleases.removed.releases.size} removed")

        newReleases
            .updated
            .releases
            .forEach { LOGGER.info("Updated $vendor ${it.releaseName}") }

        newReleases
            .added
            .releases
            .forEach { LOGGER.info("Added $vendor ${it.releaseName}") }

        newReleases
            .removed
            .releases
            .forEach { LOGGER.info("Deleted $vendor ${it.releaseName}") }

        releasesBeforeUpdate
            ?.releases
            ?.filter { release ->
                releasesAfter
                    .releases
                    .none { ModelComparators.RELEASE.compare(release, it) == 0 }
            }
            ?.forEach {
                LOGGER.error("Release disappeared or has mutated, contact $vendor to find out why ${it.releaseName} ${it.releaseLink}")
            }
    }
}
