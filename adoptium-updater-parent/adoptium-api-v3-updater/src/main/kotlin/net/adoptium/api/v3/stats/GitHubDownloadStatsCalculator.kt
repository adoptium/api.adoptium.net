package net.adoptium.api.v3.stats

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.Vendor
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

@ApplicationScoped
open class GitHubDownloadStatsCalculator @Inject constructor(private val database: ApiPersistence) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        fun getStats(repos: AdoptRepos): List<GitHubDownloadStatsDbEntry> {
            val date: ZonedDateTime = TimeSource.now()
            return repos
                .repos
                .values
                .map { featureRelease ->
                    val total = featureRelease
                        .releases
                        .getReleases()
                        .filter { it.vendor == Vendor.getDefault() }
                        .sumOf {
                            it.download_count.toInt()
                        }

                    // Tally up jvmImpl download stats
                    val jvmImplMap: Map<JvmImpl, Long> = JvmImpl.entries
                        .associateWith { jvmImpl ->
                            featureRelease
                                .releases
                                .getReleases()
                                .filter { it.vendor == Vendor.getDefault() }
                                .sumOf {
                                    it.binaries
                                        .filter { binary -> binary.jvm_impl == jvmImpl }
                                        .sumOf { binary ->
                                            binary.download_count.toInt()
                                        }
                                }
                                .toLong()
                        }

                    GitHubDownloadStatsDbEntry(
                        date,
                        total.toLong(),
                        jvmImplMap,
                        featureRelease.featureVersion
                    )
                }
                .toList()
        }
    }

    suspend fun saveStats(repos: AdoptRepos) {

        val stats = getStats(repos)

        database.addGithubDownloadStatsEntries(stats)

        printSizeStats(repos)
    }

    private fun printSizeStats(repos: AdoptRepos) {
        val stats = repos
            .repos
            .values
            .sumOf { featureRelease ->
                val total = featureRelease
                    .releases
                    .getReleases()
                    .filter { it.vendor == Vendor.getDefault() }
                    .flatMap { release ->
                        release
                            .binaries
                            .map { it.`package`.size + if (it.installer == null) 0 else it.installer!!.size }
                            .asSequence()
                    }
                    .sum()

                LOGGER.info("Stats ${featureRelease.featureVersion} $total")
                total
            }
        LOGGER.info("Stats total $stats")
    }

}
