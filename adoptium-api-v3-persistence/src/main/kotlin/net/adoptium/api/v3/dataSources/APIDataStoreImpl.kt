package net.adoptium.api.v3.dataSources

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
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
    private var attestationUpdatedAt: UpdatedInfo
    private var binaryRepos: AdoptRepos
    private var attestationRepos: AdoptAttestationRepos
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

        fun loadAttestationDataFromDb(
            dataStore: ApiPersistence,
            previousUpdateInfo: UpdatedInfo,
            forceUpdate: Boolean,
            previousRepo: AdoptAttestationRepos?,
            logEntries: Boolean = true): Pair<AdoptAttestationRepos, UpdatedInfo> {

            return runBlocking {
                val updated = dataStore.getAttestationUpdatedAt()

                if (previousRepo == null || forceUpdate || updated != previousUpdateInfo) {
                    val data = dataStore.readAttestationData()
                    val updatedAt = dataStore.getAttestationUpdatedAt()

                    val newData = AdoptAttestationRepos(data)

                    if (logEntries) {
                        LOGGER.info("Loaded Attestations: $updatedAt")
                        showAttestationStats(previousRepo, newData)
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

        private fun showAttestationStats(attRepos: AdoptAttestationRepos?, newData: AdoptAttestationRepos) {
            newData.repos
                .forEach { att ->
                    val oldAtt = attRepos?.repos?.firstOrNull { it.id == att.id }

                    if (oldAtt == null) {
                        LOGGER.info("New Attestation: ${att.release_name} ${att.filename}")
                    } else if (oldAtt != att) {
                        LOGGER.info("Modified Attestation: ${att.release_name} ${att.filename}")
                    }
                }

            attRepos?.repos
                ?.forEach { oldAtt ->
                    val newAtt = newData.repos.firstOrNull { it.id == oldAtt.id }
                    if (newAtt == null) {
                        LOGGER.info("Removed Attestation: ${oldAtt.release_name} ${oldAtt.filename}")
                    }
                }
        }


    }

    @Inject
    constructor(dataStore: ApiPersistence) {
        this.dataStore = dataStore

        updatedAt = UpdatedInfo(ZonedDateTime.now().minusYears(10), "111", 0)
        attestationUpdatedAt = UpdatedInfo(ZonedDateTime.now().minusYears(10), "111", 0)
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

        attestationRepos = try {
            val update = loadAttestationDataFromDb(
                dataStore,
                attestationUpdatedAt,
                true,
                null
            )
            attestationUpdatedAt = update.second
            update.first
        } catch (e: Exception) {
            LOGGER.error("Failed to read attestation db", e)
            AdoptAttestationRepos(listOf())
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

    override fun loadAttestationDataFromDb(
        forceUpdate: Boolean,
        logEntries: Boolean
    ): AdoptAttestationRepos {
        val update = loadAttestationDataFromDb(
            dataStore,
            attestationUpdatedAt,
            forceUpdate,
            attestationRepos,
            logEntries
        )

        this.attestationUpdatedAt = update.second
        this.attestationRepos = update.first

        return attestationRepos

    }

    override fun getUpdateInfo(): UpdatedInfo {
        return updatedAt
    }

    override fun getAttestationUpdateInfo(): UpdatedInfo {
        return attestationUpdatedAt
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

    override fun getAdoptAttestationRepos(): AdoptAttestationRepos {
        return attestationRepos
    }       
                
    override fun setAdoptAttestationRepos(attestationRepos: AdoptAttestationRepos) {
        this.attestationRepos = attestationRepos
    } 

    private fun periodicUpdate() {
        // Must catch errors or may kill the scheduler
        try {
            LOGGER.debug("periodicUpdate")
            binaryRepos = loadDataFromDb(false)
            attestationRepos = loadAttestationDataFromDb(false)
            releaseInfo = loadReleaseInfo()
        } catch (e: Exception) {
            LOGGER.error("Failed to load db", e)
        }
    }

    override fun getReleaseInfo(): ReleaseInfo {
        return releaseInfo
    }
}
