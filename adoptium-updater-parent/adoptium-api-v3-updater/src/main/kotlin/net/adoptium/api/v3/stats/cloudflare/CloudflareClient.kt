package net.adoptium.api.v3.stats.cloudflare

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.HttpClientFactory
import net.adoptium.api.v3.config.APIConfig
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.concurrent.FutureCallback
import org.apache.http.entity.StringEntity
import org.apache.http.nio.client.HttpAsyncClient
import jakarta.inject.Named
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Exception hierarchy for Cloudflare API errors
 */
sealed class CloudflareApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CloudflareServiceUnavailableException(message: String) : CloudflareApiException(message)
class CloudflareRateLimitException(message: String) : CloudflareApiException(message)
class CloudflareAuthException(message: String) : CloudflareApiException(message)
class CloudflareQueryException(message: String) : CloudflareApiException(message)
class CloudflareDatasetLimitException(message: String) : CloudflareApiException(message)
class CloudflareInternalErrorException(message: String) : CloudflareApiException(message)

@ApplicationScoped
class CloudflareClient @Inject constructor(
    @Named(HttpClientFactory.REDIRECTING)
    private val httpClient: HttpAsyncClient
) {
    companion object {
        const val CLOUDFLARE_GRAPHQL_URL = "https://api.cloudflare.com/client/v4/graphql"
        const val MAX_RETRY_ATTEMPTS = 20
        const val BASE_RETRY_DELAY_MS = 5000L
        // This set a limit of 20k items
        const val MAX_RESULTS_PER_PAGE = 1000
        const val MAX_PAGES = 20

        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        private val mapper = ObjectMapper()

        // GraphQL query with variables - static constant for reuse
        // Pagination uses clientRequestPath_gt to filter out first page
        private const val GRAPHQL_QUERY = """
            query (${'$'}zoneTag: String!, ${'$'}limit: Int!, ${'$'}startDate: Time!, ${'$'}endDate: Time!, ${'$'}lastPath: String) {
                viewer {
                    zones(filter: { zoneTag: ${'$'}zoneTag }) {
                        httpRequestsAdaptiveGroups(
                            limit: ${'$'}limit,
                            orderBy: [date_ASC, clientRequestPath_ASC],
                            filter: {
                                date_geq: ${'$'}startDate,
                                date_lt: ${'$'}endDate,
                                clientRequestPath_gt: ${'$'}lastPath,
                                clientRequestHTTPHost: "packages.adoptium.net",
                                OR: [
                                    {
                                        AND: [
                                            {clientRequestPath_like: "%.deb"},
                                            {clientRequestPath_notlike: "%/adoptium-ca-certificates%"}
                                        ]
                                    },
                                    {clientRequestPath_like: "%.rpm"},
                                    {clientRequestPath_like: "%.apk"}
                                ],
                                originResponseStatus: 200
                            }
                        ) {
                            count
                            dimensions {
                                date
                                clientRequestPath
                            }
                        }
                    }
                }
            }
        """
    }

    private val apiToken: String = APIConfig.ENVIRONMENT["CLOUDFLARE_API_TOKEN"]
        ?: throw IllegalStateException("CLOUDFLARE_API_TOKEN not set")
    private val zoneTag: String = APIConfig.ENVIRONMENT["CLOUDFLARE_ZONE_TAG"]
        ?: throw IllegalStateException("CLOUDFLARE_ZONE_TAG not set")

    /**
     * Fetches all download stats from Cloudflare with automatic pagination.
     * 
     * Cloudflare has a 1000 result limit per query. If we hit this limit,
     * we use filtering with date_gt and clientRequestPath_geq to fetch additional pages.
     *
     * @param startDate inclusive start date
     * @param endDate exclusive end date
     * @return Aggregated response from all pages
     */
    suspend fun fetchDownloadStats(startDate: ZonedDateTime, endDate: ZonedDateTime): CloudflareResponse {
        val allStats = sortedSetOf<CloudflarePackageStats>()
        var lastPath: String? = null
        var pageCount = 0

        do {
            pageCount++
            LOGGER.debug("Fetching page $pageCount with filter: clientRequestPath_gt=$lastPath")

            val response = executeQueryWithRetry(startDate, endDate, lastPath)
            val pageStats = response.data

            if (pageStats.isEmpty()) {
                LOGGER.debug("No more results on page $pageCount")
                break
            }

            allStats.addAll(pageStats)

            // Check if we hit the limit and need to paginate
            if (pageStats.size >= MAX_RESULTS_PER_PAGE) {
                // Data is already sorted, get last item directly
                val lastItem = pageStats.last()
                lastPath = lastItem.path

                LOGGER.info("Hit ${MAX_RESULTS_PER_PAGE} result limit on page $pageCount. " +
                    "Fetching next page with clientRequestPath_gt=$lastPath")
            } else {
                // Last page
                lastPath = null
            }

        } while (lastPath != null && pageCount < MAX_PAGES) // Safety limit of 20 pages, that's 20,000 results

        if (pageCount >= MAX_PAGES) {
            LOGGER.warn("Reached maximum page limit ($MAX_PAGES). Some data may not have been fetched.")
        }

        LOGGER.info("Fetched ${allStats.size} total stats across $pageCount pages")
        return CloudflareResponse(allStats)
    }

    /**
     * Execute GraphQL query with retry logic for transient failures.
     */
    private suspend fun executeQueryWithRetry(
        startDate: ZonedDateTime,
        endDate: ZonedDateTime,
        lastPath: String? = null
    ): CloudflareResponse {
        var retryCount = 0

        while (retryCount <= MAX_RETRY_ATTEMPTS) {
            try {
                return executeQuery(startDate, endDate, lastPath)
            } catch (e: CloudflareApiException) {
                when (e) {
                    is CloudflareServiceUnavailableException,
                    is CloudflareInternalErrorException -> {
                        if (retryCount >= MAX_RETRY_ATTEMPTS) {
                            LOGGER.error("Max retry attempts ($MAX_RETRY_ATTEMPTS) exceeded for ${e::class.simpleName}")
                            throw e
                        }
                        retryCount++
                        val delayMs = BASE_RETRY_DELAY_MS * retryCount
                        LOGGER.warn("${e::class.simpleName} (attempt $retryCount/$MAX_RETRY_ATTEMPTS), retrying in ${delayMs}ms: ${e.message}")
                        delay(delayMs)
                    }
                    is CloudflareRateLimitException -> {
                        if (retryCount >= MAX_RETRY_ATTEMPTS) {
                            LOGGER.error("Max retry attempts ($MAX_RETRY_ATTEMPTS) exceeded for rate limit")
                            throw e
                        }
                        retryCount++
                        val delayMs = BASE_RETRY_DELAY_MS * 2 * retryCount
                        LOGGER.warn("Rate limit hit (attempt $retryCount/$MAX_RETRY_ATTEMPTS), retrying in ${delayMs}ms: ${e.message}")
                        delay(delayMs)
                    }
                    else -> {
                        // Non-retryable errors: Auth, DatasetLimit, Query
                        LOGGER.error("Non-retryable error: ${e::class.simpleName}: ${e.message}")
                        throw e
                    }
                }
            }
        }

        // Should never reach here, but compiler needs it
        throw IllegalStateException("Retry loop exited unexpectedly")
    }

    /**
     * Execute a single GraphQL query and handle response/errors.
     */
    private suspend fun executeQuery(
        startDate: ZonedDateTime,
        endDate: ZonedDateTime,
        lastPath: String? = null
    ): CloudflareResponse {
        val query = buildGraphQLQuery(startDate, endDate, lastPath)
        val request = HttpPost(CLOUDFLARE_GRAPHQL_URL).apply {
            setHeader("Authorization", "Bearer $apiToken")
            setHeader("Content-Type", "application/json")
            entity = StringEntity(query)
        }

        val response: HttpResponse = suspendCoroutine { continuation ->
            httpClient.execute(request, object : FutureCallback<HttpResponse> {
                override fun completed(result: HttpResponse?) {
                    if (result == null) {
                        continuation.resumeWithException(Exception("No response body"))
                    } else {
                        continuation.resume(result)
                    }
                }

                override fun failed(e: Exception?) {
                    if (e == null) {
                        continuation.resumeWithException(Exception("Failed with unknown error"))
                    } else {
                        continuation.resumeWithException(e)
                    }
                }

                override fun cancelled() {
                    continuation.resumeWithException(Exception("Request cancelled"))
                }
            })
        }

        val statusCode = response.statusLine.statusCode
        val body = response.entity.content.bufferedReader().readText()
        
        // Handle HTTP status codes first
        when (statusCode) {
            401 -> throw CloudflareAuthException("Unauthorized - API token is missing, expired, or invalid")
            403 -> throw CloudflareAuthException("Forbidden - token does not have required permissions for zone $zoneTag")
            429 -> throw CloudflareRateLimitException("Rate limit exceeded (HTTP 429)")
            500 -> throw CloudflareInternalErrorException("Internal server error (HTTP 500)")
            503 -> throw CloudflareServiceUnavailableException("Service unavailable (HTTP 503)")
            504 -> throw CloudflareServiceUnavailableException("Gateway timeout (HTTP 504)")
        }
        
        if (statusCode != 200) {
            throw CloudflareInternalErrorException("Cloudflare API returned unexpected status code: $statusCode, body: $body")
        }

        // Parse response and check for GraphQL errors
        val cfResponse = CloudflareResponse.fromJson(body)
        
        // Check for GraphQL-level errors in the JSON
        val errors = parseGraphQLErrors(body)
        if (errors.isNotEmpty()) {
            handleGraphQLErrors(errors)
        }
        
        return cfResponse
    }

    /**
     * Parse GraphQL errors from JSON response.
     */
    private fun parseGraphQLErrors(json: String): List<GraphQLError> {
        return try {
            CloudflareResponse.parseErrors(json)
        } catch (e: Exception) {
            LOGGER.warn("Failed to parse GraphQL errors: ${e.message}")
            emptyList()
        }
    }

    /**
     * Handle GraphQL errors by mapping them to appropriate exceptions.
     * https://developers.cloudflare.com/analytics/graphql-api/errors/#common-error-types
     */
    private fun handleGraphQLErrors(errors: List<GraphQLError>) {
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
                
                // Auth errors (shouldn't happen with 200 response, but check anyway)
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

    private fun buildGraphQLQuery(
        startDate: ZonedDateTime,
        endDate: ZonedDateTime,
        lastPath: String? = null
    ): String {
        val variables = mutableMapOf<String, Any>(
            "zoneTag" to zoneTag,
            "limit" to MAX_RESULTS_PER_PAGE,
            "startDate" to startDate.toLocalDate().atStartOfDay().toString(),
            "endDate" to endDate.toLocalDate().toString()
        )

        lastPath?.let { variables["lastPath"] = it }

        return mapper.writeValueAsString(mapOf("query" to GRAPHQL_QUERY, "variables" to variables))
    }
}

data class GraphQLError(
    val message: String,
    val path: List<String>? = null,
    val timestamp: String? = null
)
