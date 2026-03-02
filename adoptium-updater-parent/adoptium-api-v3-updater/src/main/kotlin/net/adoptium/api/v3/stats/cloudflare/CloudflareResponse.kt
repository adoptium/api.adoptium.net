package net.adoptium.api.v3.stats.cloudflare

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDate


data class CloudflarePackageStats(
    val date: LocalDate,
    val count: Long,
    val path: String
) : Comparable<CloudflarePackageStats> {

    companion object {
        private val COMPARATOR: Comparator<CloudflarePackageStats> = compareBy<CloudflarePackageStats>(
            { it.date }, { it.path }, { it.count }
        )
    }

    override fun compareTo(other: CloudflarePackageStats): Int = COMPARATOR.compare(this, other)
}

private object ResponseKey {
    const val DATA = "data"
    const val VIEWER = "viewer"
    const val ZONES = "zones"
    const val DIMENSIONS = "dimensions"
    const val COUNT = "count"
    const val DATE = "date"
    const val PATH = "clientRequestPath"
    const val HTTP_REQUESTS_ADAPTIVE_GROUPS = "httpRequestsAdaptiveGroups"
    const val ERRORS = "errors"
    const val MESSAGE = "message"
    const val EXTENSIONS = "extensions"
    const val TIMESTAMP = "timestamp"
}

data class CloudflareResponse(
    val data: Set<CloudflarePackageStats> = sortedSetOf()
) {
    companion object {
        private val mapper = ObjectMapper()

        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        /**
         * Parse GraphQL errors from JSON response.
         */
        private fun parseErrors(json: String): List<GraphQLError> {
            val root = mapper.readTree(json)
            val errorsNode = root.path(ResponseKey.ERRORS)

            if (errorsNode.isMissingNode || !errorsNode.isArray) {
                return emptyList()
            }

            return errorsNode.map { errorNode ->
                val message = errorNode.path(ResponseKey.MESSAGE).asText() ?: "Unknown error"
                val path = errorNode.path("path").map { it.asText() }
                val timestamp = errorNode.path(ResponseKey.EXTENSIONS).path(ResponseKey.TIMESTAMP).asText()
                GraphQLError(message, path, timestamp)
            }
        }

        /**
         * Handle GraphQL errors by mapping them to appropriate exceptions.
         * Throws the first matching exception found.
         *
         * https://developers.cloudflare.com/analytics/graphql-api/errors/#common-error-types
         */
        private fun handleErrors(errors: List<GraphQLError>) {
            for (error in errors) {
                val message = error.message

                when {
                    // Service unavailability
                    message.contains("unable to execute query", ignoreCase = true) ||
                    message.contains("too many queries in progress", ignoreCase = true) -> {
                        throw CloudflareServiceUnavailableException(message)
                    }

                    // Rate limits
                    message.contains("rate limiter budget depleted", ignoreCase = true) ||
                    message.contains("too many nodes", ignoreCase = true) ||
                    message.contains("excessive resources", ignoreCase = true) -> {
                        throw CloudflareRateLimitException(message)
                    }

                    // Dataset limits
                    message.contains("cannot request data older than", ignoreCase = true) ||
                    message.contains("number of fields can't be more than", ignoreCase = true) ||
                    message.contains("limit must be positive", ignoreCase = true) ||
                    message.contains("query time range is too large", ignoreCase = true) -> {
                        throw CloudflareDatasetLimitException(message)
                    }

                    // Query parsing issues
                    message.contains("error parsing args", ignoreCase = true) ||
                    message.contains("scalar fields must have no selections", ignoreCase = true) ||
                    message.contains("object field must have selections", ignoreCase = true) ||
                    message.contains("unknown field", ignoreCase = true) ||
                    message.contains("query contains error", ignoreCase = true) -> {
                        throw CloudflareQueryException(message)
                    }

                    // Auth errors
                    message.contains("Unauthorized", ignoreCase = true) ||
                    message.contains("not authorized", ignoreCase = true) ||
                    message.contains("does not have access", ignoreCase = true) -> {
                        throw CloudflareAuthException(message)
                    }

                    // Internal server error
                    message.contains("Internal server error", ignoreCase = true) -> {
                        throw CloudflareInternalErrorException(message)
                    }

                    else -> {
                        LOGGER.warn("Unrecognized GraphQL error: $message")
                    }
                }
            }
        }

        /**
         * Parse JSON response. Checks for GraphQL errors first and throws appropriate exceptions.
         * Only returns data if no errors are present.
         *
         * @return net.adoptium.api.v3.stats.cloudflare.CloudflareResponse or throws CloudflareApiException
         */
        fun fromJson(json: String): CloudflareResponse {
            val root = mapper.readTree(json)

            // Check for GraphQL-level errors first
            val errors = parseErrors(json)
            if (errors.isNotEmpty()) {
                handleErrors(errors)
            }

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
