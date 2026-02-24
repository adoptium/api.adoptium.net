package net.adoptium.api.v3.stats.cloudflare

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
import org.slf4j.LoggerFactory
import java.time.ZoneId

@ApplicationScoped
open class CloudflareStatsCalculator @Inject constructor(
    private val database: ApiPersistence,
    private val cloudFlareClient: CloudflareClient
) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        // TODO: Extract version from clientRequestPath - need real examples to implement
        fun extractVersionFromPath(path: String): Int {
            return 0
        }
    }

    suspend fun updateDb() {
        try {
            val endDate = TimeSource.now()
            val startDate = endDate.minusDays(1)

            LOGGER.info("Fetching CloudFlare package stats from $startDate to $endDate")

            val response = cloudFlareClient.fetchDownloadStats(startDate, endDate)

            val stats = response.data
                .groupingBy { stats ->
                    val version = extractVersionFromPath(stats.path)
                    // This is a hack to put all stats of the same day together
                    stats.date to version
                }
                .fold(0L) { currentTotal, element ->
                    currentTotal + element.count
                }
                .map { (key, totalDownloads) ->
                    CloudflarePackageDownloadStatsDbEntry(
                        date = key.first.atStartOfDay(ZoneId.of("UTC")),
                        feature_version = key.second,
                        downloads = totalDownloads
                    )
                }

            database.addPackageDownloadStatsEntries(stats)
            LOGGER.info("Saved CloudFlare package stats: $stats downloads")
        } catch (e: Exception) {
            LOGGER.error("Failed to fetch CloudFlare package stats", e)
            throw e
        }
    }
}
