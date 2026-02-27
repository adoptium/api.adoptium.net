package net.adoptium.api.v3.stats.cloudflare

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDate


data class CloudflarePackageStats(
    val date: LocalDate,
    val count: Long,
    val path: String
)

private object ResponseKey {
    const val DATA = "data"
    const val VIEWER = "viewer"
    const val ZONES = "zones"
    const val DIMENSIONS = "dimensions"
    const val COUNT = "count"
    const val DATE = "date"
    const val PATH = "clientRequestPath"
    const val HTTP_REQUESTS_ADAPTIVE_GROUPS = "httpRequestsAdaptiveGroups"
}

data class CloudflareResponse(
    val data: Set<CloudflarePackageStats>
) {
    companion object {
        private val mapper = ObjectMapper()
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        fun fromJson(json: String): CloudflareResponse {
            val root = mapper.readTree(json)
            val dataList = mutableSetOf<CloudflarePackageStats>()

            val zones = root.path(ResponseKey.DATA).path(ResponseKey.VIEWER).path(ResponseKey.ZONES)
            if (!zones.isArray) {
                LOGGER.warn("Cloudflare response is missing zone data")
                return CloudflareResponse(dataList)
            }

            for (zone in zones) {
                val groups = zone.path(ResponseKey.HTTP_REQUESTS_ADAPTIVE_GROUPS)
                if (!groups.isArray) {
                    LOGGER.warn("Missing ${ResponseKey.HTTP_REQUESTS_ADAPTIVE_GROUPS} from response, query might has changed")
                    continue
                }
                for (group in groups) {
                    val countNode = group.path(ResponseKey.COUNT)
                    val dimensions = group.path(ResponseKey.DIMENSIONS)

                    if (countNode.isMissingNode || dimensions.isMissingNode) {
                        LOGGER.warn("There is a group with missing information $group")
                        continue
                    }

                    val dateStr = dimensions.path(ResponseKey.DATE).asText()
                    val path = dimensions.path(ResponseKey.PATH).asText()
                    if (dateStr.isNullOrBlank() || path.isNullOrBlank()) {
                        LOGGER.warn("There is a group with missing information $group")
                        continue
                    }

                    val count = countNode.asLong()
                    if (count > 0) {
                        val date = LocalDate.parse(dateStr)
                        dataList.add(CloudflarePackageStats(date, count, path))
                    }
                }
            }

            return CloudflareResponse(dataList)
        }
    }

    /**
     * Merges two CloudflareResponses, aggregating counts for entries with the same path and date.
     */
    fun merge(other: CloudflareResponse): CloudflareResponse {
        val mergedData = (this.data + other.data)
            .groupBy { it.path to it.date }
            // I haven't seen this, but it could be possible or at least the doc doesn't discard it.
            .map { (key, entries) ->
                CloudflarePackageStats(
                    date = key.second,
                    count = entries.sumOf { it.count },
                    path = key.first
                )
            }
            .toSet()
        return CloudflareResponse(mergedData)
    }
}
