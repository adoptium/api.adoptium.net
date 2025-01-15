package net.adoptium.api.v3

import com.mongodb.MongoException
import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.ApplicationPath
import jakarta.ws.rs.core.Application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.ReleaseVersionResolver
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.releaseNotes.AdoptReleaseNotes
import net.adoptium.api.v3.stats.StatsInterface
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timerTask
import kotlin.system.exitProcess

@UnlessBuildProfile("test")
@ApplicationScoped
@ApplicationPath("/")
@Startup
class V3UpdaterApp : Application()

@ApplicationScoped
class V3Updater @Inject constructor(
    private val adoptReposBuilder: AdoptReposBuilder,
    private val apiDataStore: APIDataStore,
    private val database: ApiPersistence,
    private val statsInterface: StatsInterface,
    private val releaseVersionResolver: ReleaseVersionResolver,
    private val adoptReleaseNotes: AdoptReleaseNotes,
    private val updatableVersionSupplier: UpdatableVersionSupplier
) : Updater {

    private val mutex = Mutex()
    private val toUpdate: ConcurrentSkipListSet<String> = ConcurrentSkipListSet<String>()

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        fun calculateChecksum(repo: AdoptRepos): String {
            val md = MessageDigest.getInstance("SHA256")
            val outputStream = object : OutputStream() {
                override fun write(b: Int) {
                    md.update(b.toByte())
                }
            }
            UpdaterJsonMapper.mapper.writeValue(outputStream, repo)

            return String(Base64.getEncoder().encode(md.digest()))
        }

        fun copyOldReleasesIntoNewRepo(currentRepo: AdoptRepos, newRepoData: AdoptRepos, filter: ReleaseIncludeFilter) = newRepoData
            .addAll(currentRepo
                .allReleases
                .getReleases()
                .filter { !filter.filter(it.vendor, it.updated_at.dateTime, it.release_type == ReleaseType.ea) }
                .toList())
    }

    override fun addToUpdate(toUpdate: String): List<Release> {
        val repo = apiDataStore.loadDataFromDb(true)
        val toUpdateList = repo
            .allReleases
            .getReleases()
            .filter { release ->
                release.release_name == toUpdate
            }
            .toList()

        if (toUpdateList.isNotEmpty()) {
            toUpdate.plus(toUpdate)
        }

        return toUpdateList
    }

    private fun incrementalUpdate(oldRepo: AdoptRepos): AdoptRepos? {
        return runBlocking {
            // Must catch errors or may kill the scheduler
            try {
                LOGGER.info("Starting Incremental update")
                val toUpdateTmp = HashSet<String>()
                toUpdateTmp.addAll(toUpdate)
                toUpdate.clear()
                val updatedRepo = adoptReposBuilder.incrementalUpdate(
                    toUpdateTmp,
                    oldRepo,
                    database::getGhReleaseMetadata
                )

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
                    LOGGER.debug("Release disappeared ${releaseA.id} ${releaseA.version_data.semver}")
                } else if (releaseA != releaseB) {
                    LOGGER.debug("Release changedA {}", releaseA)
                    LOGGER.debug("Release changedB {}", releaseB)
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
        assertConnectedToDb()

        val executor = Executors.newScheduledThreadPool(2)

        val delay = if (instantFullUpdate) 0L else 1L

        var repo: AdoptRepos = try {
            apiDataStore.loadDataFromDb(true)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Failed to load db", e)
            if (e is MongoException) {
                LOGGER.error("Failed to connect to db, exiting")
                Quarkus.asyncExit(2)
                Quarkus.waitForExit()
            }
            AdoptRepos(emptyList())
        }

        val incrementalUpdateScheduled = AtomicBoolean(false)

        executor.scheduleWithFixedDelay(
            timerTask {
                repo = fullUpdate(repo, true) ?: repo
                if (!incrementalUpdateScheduled.getAndSet(true)) {
                    executor.scheduleWithFixedDelay(
                        timerTask {
                            repo = incrementalUpdate(repo) ?: repo
                        },
                        1, 6, TimeUnit.MINUTES
                    )
                }
                repo = fullUpdate(repo, false) ?: repo
            },
            delay, 1, TimeUnit.DAYS
        )
    }

    private fun assertConnectedToDb() {
        val connected = try {
            runBlocking {
                return@runBlocking apiDataStore.isConnectedToDb()
            }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Failed to load db", e)
            false
        }

        if (!connected) {
            LOGGER.error("Failed to connect to db, exiting process")
            Quarkus.asyncExit(2)
            Quarkus.waitForExit()
        }
    }

    private fun fullUpdate(currentRepo: AdoptRepos, releasesOnly: Boolean): AdoptRepos? {
        // Must catch errors or may kill the scheduler
        try {
            return runBlocking {
                LOGGER.info("Starting Full update {}", releasesOnly)

                updatableVersionSupplier.updateVersions()

                val filterType: ReleaseFilterType = if (releasesOnly) {
                    ReleaseFilterType.RELEASES_ONLY
                } else {
                    ReleaseFilterType.ALL
                }

                val filter = ReleaseIncludeFilter(TimeSource.now(), filterType)

                val newRepoData = adoptReposBuilder.build(filter)

                val repo = copyOldReleasesIntoNewRepo(currentRepo, newRepoData, filter)

                val checksum = calculateChecksum(repo)

                mutex.withLock {
                    runBlocking {
                        database.updateAllRepos(repo, checksum)
                        statsInterface.update(repo)
                        database.setReleaseInfo(releaseVersionResolver.formReleaseInfo(repo))
                    }
                }

                LOGGER.info("Updating Release Notes")
                adoptReleaseNotes.updateReleaseNotes(repo)

                printRepoDebugInfo(currentRepo, repo, repo)

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
