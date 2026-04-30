package net.adoptium.api.v3.dataSources

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.ReleaseInfo
import net.adoptium.api.v3.models.Vendor
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

@ApplicationScoped
open class APIDataStoreImpl : APIDataStore {
    private var dataStore: ApiPersistence
    private var updatedAt: UpdatedInfo
    private var cdxaUpdatedAt: UpdatedInfo
    private var binaryRepos: AdoptRepos
    private var cdxaRepos: AdoptCdxaRepos
    private var releaseInfo: ReleaseInfo
    private var schedule: ScheduledFuture<*>?

    // required as injection objects to the final field
    open fun getSchedule() = schedule

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private val MAX_VERSION_TO_LOAD = (System.getenv("MAX_VERSION_TO_LOAD") ?: "100").toInt()

        fun loadDataFromDb(
            dataStore: ApiPersistence,
            previousUpdateInfo: UpdatedInfo,
            forceUpdate: Boolean,
            previousRepo: AdoptRepos?,
            versions: List<Int>,
            logEntries: Boolean = true): Pair<AdoptRepos, UpdatedInfo> {

            return runBlocking {
                val updated = dataStore.getUpdatedAt()

                if (previousRepo == null || forceUpdate || updated != previousUpdateInfo) {
                    val data = versions
                        .map { version ->
                            dataStore.readReleaseData(version)
                        }
                        .filter { it.releases.nodes.isNotEmpty() }
                        .toList()
                    val updatedAt = dataStore.getUpdatedAt()

                    val newData = filterValidAssets(data)

                    if (logEntries) {
                        LOGGER.info("Loaded Version: $updatedAt")
                        showStats(previousRepo, newData)
                    }
                    Pair(newData, updatedAt)
                } else {
                    Pair(previousRepo, previousUpdateInfo)
                }
            }
        }

        fun loadCdxaDataFromDb(
            dataStore: ApiPersistence,
            previousUpdateInfo: UpdatedInfo,
            forceUpdate: Boolean,
            previousRepo: AdoptCdxaRepos?,
            logEntries: Boolean = true): Pair<AdoptCdxaRepos, UpdatedInfo> {

            return runBlocking {
                val updated = dataStore.getCdxaUpdatedAt()

                if (previousRepo == null || forceUpdate || updated != previousUpdateInfo) {
                    val data = dataStore.readCdxaData()
                    val updatedAt = dataStore.getCdxaUpdatedAt()

                    val newData = AdoptCdxaRepos(data)

                    if (logEntries) {
                        LOGGER.info("Loaded Cdxas: $updatedAt")
                        showCdxaStats(previousRepo, newData)
                    }
                    Pair(newData, updatedAt)
                } else {
                    Pair(previousRepo, previousUpdateInfo)
                }
            }
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

        private fun showCdxaStats(attRepos: AdoptCdxaRepos?, newData: AdoptCdxaRepos) {
            newData.repos
                .forEach { att ->
                    val oldAtt = attRepos?.repos?.firstOrNull { it.id == att.id }

                    if (oldAtt == null) {
                        LOGGER.info("New Cdxa: ${att.release_name} ${att.filename}")
                    } else if (oldAtt != att) {
                        LOGGER.info("Modified Cdxa: ${att.release_name} ${att.filename}")
                    }
                }

            attRepos?.repos
                ?.forEach { oldAtt ->
                    val newAtt = newData.repos.firstOrNull { it.id == oldAtt.id }
                    if (newAtt == null) {
                        LOGGER.info("Removed Cdxa: ${oldAtt.release_name} ${oldAtt.filename}")
                    }
                }
        }


    }

    @Inject
    constructor(dataStore: ApiPersistence) {
        this.dataStore = dataStore

        updatedAt = UpdatedInfo(ZonedDateTime.now().minusYears(10), "111", 0)
        cdxaUpdatedAt = UpdatedInfo(ZonedDateTime.now().minusYears(10), "111", 0)
        schedule = null

        binaryRepos = try {
            val update = loadDataFromDb(
                dataStore,
                updatedAt,
                true,
                null,
                (8..MAX_VERSION_TO_LOAD).toList()
            )
            updatedAt = update.second
            update.first
        } catch (e: Exception) {
            LOGGER.error("Failed to read db", e)
            AdoptRepos(listOf())
        }

        cdxaRepos = try {
            val update = loadCdxaDataFromDb(
                dataStore,
                cdxaUpdatedAt,
                true,
                null
            )
            cdxaUpdatedAt = update.second
            update.first
        } catch (e: Exception) {
            LOGGER.error("Failed to read cdxa db", e)
            AdoptCdxaRepos(listOf())
        }

        releaseInfo = loadReleaseInfo()
    }

    override fun schedulePeriodicUpdates() {
        if (schedule == null) {
            LOGGER.debug("schedulePeriodicUpdates")
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

    override fun loadDataFromDb(
        forceUpdate: Boolean,
        logEntries: Boolean
    ): AdoptRepos {
        // Scan the currently available versions plus 5
        val versions = releaseInfo.available_releases.toList()
            .plus((releaseInfo.available_releases.last()..releaseInfo.available_releases.last() + 5))
            .filter { it <= MAX_VERSION_TO_LOAD }

        val update = loadDataFromDb(
            dataStore,
            updatedAt,
            forceUpdate,
            binaryRepos,
            versions,
            logEntries
        )

        this.updatedAt = update.second
        this.binaryRepos = update.first

        return binaryRepos

    }

    override fun loadCdxaDataFromDb(
        forceUpdate: Boolean,
        logEntries: Boolean
    ): AdoptCdxaRepos {
        val update = loadCdxaDataFromDb(
            dataStore,
            cdxaUpdatedAt,
            forceUpdate,
            cdxaRepos,
            logEntries
        )

        this.cdxaUpdatedAt = update.second
        this.cdxaRepos = update.first

        return cdxaRepos

    }

    override fun getUpdateInfo(): UpdatedInfo {
        return updatedAt
    }

    override fun getCdxaUpdateInfo(): UpdatedInfo {
        return cdxaUpdatedAt
    }

    override suspend fun isConnectedToDb(): Boolean {
        return dataStore.isConnected()
    }

    // open for
    override fun getAdoptRepos(): AdoptRepos {
        return binaryRepos
    }

    override fun setAdoptRepos(binaryRepos: AdoptRepos) {
        this.binaryRepos = binaryRepos
    }

    override fun getAdoptCdxaRepos(): AdoptCdxaRepos {
        return cdxaRepos
    }       
                
    override fun setAdoptCdxaRepos(cdxaRepos: AdoptCdxaRepos) {
        this.cdxaRepos = cdxaRepos
    } 

    private fun periodicUpdate() {
        // Must catch errors or may kill the scheduler
        try {
            LOGGER.debug("periodicUpdate")
            binaryRepos = loadDataFromDb(false)
            cdxaRepos = loadCdxaDataFromDb(false)
            releaseInfo = loadReleaseInfo()
        } catch (e: Exception) {
            LOGGER.error("Failed to load db", e)
        }
    }

    override fun getReleaseInfo(): ReleaseInfo {
        return releaseInfo
    }
}
