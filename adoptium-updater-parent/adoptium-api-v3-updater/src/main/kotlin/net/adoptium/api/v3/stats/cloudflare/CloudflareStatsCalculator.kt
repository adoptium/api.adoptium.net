package net.adoptium.api.v3.stats.cloudflare

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
import org.slf4j.LoggerFactory

@ApplicationScoped
open class CloudflareStatsCalculator @Inject constructor(
    private val database: ApiPersistence,
    private val cloudFlareClient: CloudflareClient
) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        fun extractVersionFromPath(path: String): Int? {
            return try {
                path
                    .split("/temurin-")
                    .lastOrNull()
                    ?.split("-", "_")
                    ?.firstOrNull()
                    ?.toIntOrNull()
            } catch (e: Exception) {
                LOGGER.warn("Failed to extract version from path: $path", e)
                null
            }
        }
    }

    suspend fun updateDb() {
        try {
            // Fetch complete previous day data (midnight yesterday to midnight today)
            // This ensures we always get a full day of data and avoids partial/duplicate entries
            val endDate = TimeSource.now().toLocalDate().atStartOfDay(TimeSource.ZONE)
            val startDate = endDate.minusDays(1)

            LOGGER.info("Fetching CloudFlare package stats from $startDate to $endDate")

            val response = cloudFlareClient.fetchDownloadStats(startDate, endDate)

            // Group by version only (datetime is no longer part of the Cloudflare response
            // since it's been removed from the query dimensions for correct pagination)
            val stats = response.data
                .mapNotNull { stats ->
                    val version = extractVersionFromPath(stats.path)
                    version?.let { stats to version }
                }
                .groupingBy { (_, version) -> version }
                .fold(0L) { currentTotal, element ->
                    currentTotal + element.first.count
                }
                .map { (version, totalDownloads) ->
                    CloudflarePackageDownloadStatsDbEntry(
                        date = endDate,
                        feature_version = version,
                        downloads = totalDownloads
                    )
                }

            database.addPackageDownloadStatsEntries(stats)
            LOGGER.info("Saved CloudFlare package stats: ${stats.size} entries with total downloads")
        } catch (e: Exception) {
            LOGGER.error("Failed to fetch CloudFlare package stats", e)
            throw e
        }
    }
}
