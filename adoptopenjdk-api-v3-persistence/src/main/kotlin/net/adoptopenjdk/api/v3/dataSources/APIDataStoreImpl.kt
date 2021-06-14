package net.adoptopenjdk.api.v3.dataSources

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.models.Releases
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.ReleaseInfo
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.models.Versions
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.timerTask

@Singleton
open class APIDataStoreImpl : APIDataStore {
    private var dataStore: ApiPersistence
    private var updatedAt: UpdatedInfo
    private var binaryRepos: AdoptRepos
    private var releaseInfo: ReleaseInfo
    var schedule: ScheduledFuture<*>?

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    @Inject
    constructor(dataStore: ApiPersistence) {
        this.dataStore = dataStore
        updatedAt = UpdatedInfo(ZonedDateTime.now().minusYears(10), "111", 0)
        schedule = null
        binaryRepos = try {
            loadDataFromDb(true)
        } catch (e: Exception) {
            LOGGER.error("Failed to read db", e)
            AdoptRepos(listOf())
        }

        releaseInfo = loadReleaseInfo()
    }

    constructor(binaryRepos: AdoptRepos, dataStore: ApiPersistence) {
        this.dataStore = dataStore
        updatedAt = UpdatedInfo(ZonedDateTime.now().minusYears(10), "111", 0)
        schedule = null
        this.binaryRepos = binaryRepos
        this.releaseInfo = loadReleaseInfo()
    }

    override fun schedulePeriodicUpdates() {
        if (schedule == null) {
            schedule = Executors
                .newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(
                    timerTask {
                        periodicUpdate()
                    },
                    0, 1, TimeUnit.MINUTES
                )
        }
    }

    private fun loadReleaseInfo(): ReleaseInfo {
        releaseInfo = runBlocking {
            val releaseInfo = try {
                dataStore.getReleaseInfo()
            } catch (e: Exception) {
                LOGGER.error("Failed to read db", e)
                null
            }

            // Default for first time when DB is still being populated
            releaseInfo ?: ReleaseInfo(
                arrayOf(8, 9, 10, 11, 12, 13, 14),
                arrayOf(8, 11),
                11,
                14,
                15,
                15
            )
        }
        return releaseInfo
    }

    override fun loadDataFromDb(forceUpdate: Boolean): AdoptRepos {
        val previousRepo: AdoptRepos = binaryRepos

        binaryRepos = runBlocking {
            val updated = dataStore.getUpdatedAt()

            if (forceUpdate || updated != updatedAt) {
                val data = Versions
                    .versions
                    .map { version ->
                        dataStore.readReleaseData(version)
                    }
                    .filter { it.releases.nodes.isNotEmpty() }
                    .toList()
                updatedAt = dataStore.getUpdatedAt()

                LOGGER.info("Loaded Version: $updatedAt")
                val newData = filterValidAssets(data)

                showStats(previousRepo, newData)
                newData
            } else {
                binaryRepos
            }
        }

        return binaryRepos
    }

    private fun filterValidAssets(data: List<FeatureRelease>): AdoptRepos {
        // Ensure that we filter out valid releases/binaries for this ecosystem
        val filtered = AdoptRepos(data)
            .getFilteredReleases(
                { release ->
                    Vendor.validVendor(release.vendor)
                },
                { binary ->
                    JvmImpl.validJvmImpl(binary.jvm_impl)
                },
                SortOrder.ASC,
                SortMethod.DEFAULT
            )
            .groupBy { it.version_data.major }
            .map { FeatureRelease(it.key, Releases(it.value)) }

        return AdoptRepos(filtered)
    }

    // open for
    override fun getAdoptRepos(): AdoptRepos {
        return binaryRepos
    }

    override fun setAdoptRepos(binaryRepos: AdoptRepos) {
        this.binaryRepos = binaryRepos
    }

    private fun periodicUpdate() {
        // Must catch errors or may kill the scheduler
        try {
            binaryRepos = loadDataFromDb(false)
            releaseInfo = loadReleaseInfo()
        } catch (e: Exception) {
            LOGGER.error("Failed to load db", e)
        }
    }

    private fun showStats(binaryRepos: AdoptRepos?, newData: AdoptRepos) {
        newData.allReleases.getReleases()
            .forEach { release ->
                val oldRelease = binaryRepos?.allReleases?.nodes?.get(release.id)
                if (oldRelease == null) {
                    LOGGER.info("New release: ${release.release_name} ${release.binaries.size}")
                } else if (oldRelease.binaries.size != release.binaries.size) {
                    LOGGER.info("Binary count changed ${release.release_name} ${oldRelease.binaries.size} -> ${release.binaries.size}")
                }
            }

        binaryRepos?.allReleases?.getReleases()
            ?.forEach { oldRelease ->
                val newRelease = binaryRepos.allReleases.nodes[oldRelease.id]
                if (newRelease == null) {
                    LOGGER.info("Removed release: ${oldRelease.release_name} ${oldRelease.binaries.size}")
                }
            }
    }

    override fun getReleaseInfo(): ReleaseInfo {
        return releaseInfo
    }
}
