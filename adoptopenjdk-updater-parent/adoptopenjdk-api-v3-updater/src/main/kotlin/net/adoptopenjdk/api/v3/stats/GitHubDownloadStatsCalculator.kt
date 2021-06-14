package net.adoptopenjdk.api.v3.stats

import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.Vendor
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class GitHubDownloadStatsCalculator @Inject constructor(private val database: ApiPersistence) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
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
            .map { featureRelease ->
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
            .sum()
        LOGGER.info("Stats total $stats")
    }

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
                    .sumBy {
                        it.download_count.toInt()
                    }

                // Tally up jvmImpl download stats
                val jvmImplMap: Map<JvmImpl, Long> = JvmImpl.values().map { jvmImpl ->
                    jvmImpl to
                        featureRelease
                            .releases
                            .getReleases()
                            .filter { it.vendor == Vendor.getDefault() }
                            .sumBy {
                                it.binaries
                                    .filter { binary -> binary.jvm_impl == jvmImpl }
                                    .sumBy {
                                        binary ->
                                        binary.download_count.toInt()
                                    }
                            }
                            .toLong()
                }.toMap()

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
