package net.adoptopenjdk.api.v3

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.adoptopenjdk.api.v3.ai.AppInsightsTelemetry
import net.adoptopenjdk.api.v3.config.APIConfig
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.ReleaseVersionResolver
import net.adoptopenjdk.api.v3.dataSources.UpdaterJsonMapper
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.Versions
import net.adoptopenjdk.api.v3.stats.StatsInterface
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.timerTask

class V3Updater @Inject constructor(
    private val adoptReposBuilder: AdoptReposBuilder,
    private val apiDataStore: APIDataStore,
    private val database: ApiPersistence,
    private val statsInterface: StatsInterface,
    private val releaseVersionResolver: ReleaseVersionResolver
) {

    private val mutex = Mutex()

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        fun calculateChecksum(repo: AdoptRepos): String {
            val md = MessageDigest.getInstance("MD5")
            val outputStream = object : OutputStream() {
                override fun write(b: Int) {
                    md.update(b.toByte())
                }
            }
            UpdaterJsonMapper.mapper.writeValue(outputStream, repo)

            return String(Base64.getEncoder().encode(md.digest()))
        }
    }

    init {
        AppInsightsTelemetry.start()
    }

    fun incrementalUpdate(oldRepo: AdoptRepos): AdoptRepos? {
        return runBlocking {
            // Must catch errors or may kill the scheduler
            try {
                LOGGER.info("Starting Incremental update")
                val updatedRepo = adoptReposBuilder.incrementalUpdate(oldRepo)

                if (updatedRepo != oldRepo) {
                    val after = writeIncrementalUpdate(updatedRepo, oldRepo)
                    printRepoDebugInfo(oldRepo, updatedRepo, after)
                    return@runBlocking after
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to perform incremental update", e)
            }
            return@runBlocking null
        }
    }

    private fun printRepoDebugInfo(oldRepo: AdoptRepos, updatedRepo: AdoptRepos, after: AdoptRepos) {
        if (APIConfig.DEBUG) {
            LOGGER.debug("Updated and db version comparison {} {} {} {} {} {}", calculateChecksum(oldRepo), oldRepo.hashCode(), calculateChecksum(updatedRepo), updatedRepo.hashCode(), calculateChecksum(after), after.hashCode())

            LOGGER.debug("Compare db and updated")
            deepDiffDebugPrint(after, updatedRepo)

            LOGGER.debug("Compare Old and updated")
            deepDiffDebugPrint(oldRepo, updatedRepo)

            LOGGER.debug("Compare db and old")
            deepDiffDebugPrint(after, oldRepo)
        }
    }

    private fun deepDiffDebugPrint(repoA: AdoptRepos, repoB: AdoptRepos) {
        repoA
            .allReleases
            .getReleases()
            .forEach { releaseA ->
                val releaseB = repoB.allReleases.getReleaseById(releaseA.id)
                if (releaseB == null) {
                    LOGGER.debug("Release disapeared ${releaseA.id} ${releaseA.version_data.semver}")
                } else if (releaseA != releaseB) {
                    LOGGER.debug("Release changedA $releaseA")
                    LOGGER.debug("Release changedB $releaseB")
                    releaseA
                        .binaries
                        .forEach { binaryA ->
                            val binaryB = releaseB
                                .binaries
                                .firstOrNull { it.`package`.link == binaryA.`package`.link }
                            if (binaryB == null) {
                                LOGGER.debug("Binary disapeared ${binaryA.`package`.name}")
                            } else if (binaryA != binaryB) {
                                LOGGER.debug("Binary updated ${binaryA.`package`.name}")
                                LOGGER.debug(JsonMapper.mapper.writeValueAsString(binaryA))
                                LOGGER.debug(JsonMapper.mapper.writeValueAsString(binaryB))
                            }
                        }
                }
            }

        repoB
            .allReleases
            .getReleases()
            .forEach { releaseB ->
                val releaseA = repoA.allReleases.getReleaseById(releaseB.id)
                if (releaseA == null) {
                    LOGGER.info("Release Added ${releaseB.id} ${releaseB.version_data.semver}")
                }
            }
    }

    private suspend fun writeIncrementalUpdate(updatedRepo: AdoptRepos, oldRepo: AdoptRepos): AdoptRepos {
        val checksum = calculateChecksum(updatedRepo)
        val oldChecksum = calculateChecksum(oldRepo)

        if (checksum == oldChecksum) {
            return updatedRepo
        }

        return mutex.withLock {
            // Ensure that the database has not been updated since calculating the incremental update
            if (database.getUpdatedAt().checksum == oldChecksum) {
                database.updateAllRepos(updatedRepo, checksum)
                database.setReleaseInfo(releaseVersionResolver.formReleaseInfo(updatedRepo))

                LOGGER.info("Incremental update done")
                LOGGER.info("Saved version: $checksum ${updatedRepo.hashCode()}")
                return@withLock updatedRepo
            } else {
                LOGGER.info("Incremental update done")
                LOGGER.warn("Not applying incremental update due to checksum miss $checksum ${updatedRepo.hashCode()} $oldChecksum ${oldRepo.hashCode()} ${database.getUpdatedAt().checksum}")

                // re-calculate checksum in case of schema change
                val dbVersion = apiDataStore.loadDataFromDb(true)
                val dbChecksum = calculateChecksum(dbVersion)
                if (dbChecksum != database.getUpdatedAt().checksum) {
                    database.updateAllRepos(dbVersion, dbChecksum)
                }

                return@withLock dbVersion
            }
        }
    }

    fun run(instantFullUpdate: Boolean) {
        val executor = Executors.newScheduledThreadPool(2)

        val delay = if (instantFullUpdate) 0L else 1L

        var repo: AdoptRepos = try {
            apiDataStore.loadDataFromDb(true)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Failed to load db", e)
            AdoptRepos(emptyList())
        }

        executor.scheduleWithFixedDelay(
            timerTask {
                repo = fullUpdate() ?: repo
            },
            delay, 1, TimeUnit.DAYS
        )

        executor.scheduleWithFixedDelay(
            timerTask {
                repo = incrementalUpdate(repo) ?: repo
            },
            1, 3, TimeUnit.MINUTES
        )
    }

    private fun fullUpdate(): AdoptRepos? {
        // Must catch errors or may kill the scheduler
        try {
            return runBlocking {
                LOGGER.info("Starting Full update")

                val repo = adoptReposBuilder.build(Versions.versions)

                val checksum = calculateChecksum(repo)

                mutex.withLock {
                    runBlocking {
                        database.updateAllRepos(repo, checksum)
                        statsInterface.update(repo)
                        database.setReleaseInfo(releaseVersionResolver.formReleaseInfo(repo))
                    }
                }

                LOGGER.info("Full update done")
                return@runBlocking repo
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to perform full update", e)
        } catch (e: Throwable) {
            // Log and rethrow, may be unrecoverable error such as OutOfMemoryError
            LOGGER.error("Error during full update", e)
            throw e
        }
        return null
    }
}
