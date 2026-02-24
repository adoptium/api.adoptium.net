package net.adoptium.api.v3.stats.cloudflare

import com.fasterxml.jackson.databind.ObjectMapper
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
    const val COUNT = "zones"
    const val DATE = "zones"
    const val PATH = "clientRequestPath"
    const val HTTP_REQUESTS_ADAPTIVE_GROUPS = "httpRequestsAdaptiveGroups"
}

data class CloudflareResponse(
    val data: List<CloudflarePackageStats>
) {
    companion object {
        private val mapper = ObjectMapper()

        fun fromJson(json: String): CloudflareResponse {
            val root = mapper.readTree(json)
            val dataList = mutableListOf<CloudflarePackageStats>()

            val zones = root.path(ResponseKey.DATA).path(ResponseKey.VIEWER).path(ResponseKey.ZONES)
            if (zones.isArray) {
                for (zone in zones) {
                    val groups = zone.path(ResponseKey.HTTP_REQUESTS_ADAPTIVE_GROUPS)
                    if (groups.isArray) {
                        for (group in groups) {
                            // FIXME if dimension is missing or doesn't contain path or data I should skip group
                            val count = group.path(ResponseKey.COUNT).asLong()
                            val dateStr = group.path(ResponseKey.DIMENSIONS).path(ResponseKey.DATE).asText()
                            val path = group.path(ResponseKey.DIMENSIONS).path(ResponseKey.PATH).asText()

                            if (count > 0 && dateStr.isNotBlank()) {
                                val date = LocalDate.parse(dateStr)
                                dataList.add(CloudflarePackageStats(date, count, path))
                            }
                        }
                    }
                }
            }

            return CloudflareResponse(dataList)
        }
    }
}
