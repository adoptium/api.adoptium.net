package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.ktor.client.plugins.*
import io.ktor.http.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.json.JsonObject
import kotlinx.coroutines.delay
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.graphql.models.HasRateLimit
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random
import net.adoptium.api.v3.config.APIConfig

@ApplicationScoped
open class GraphQLGitHubInterface @Inject constructor(
    private val graphQLRequest: GraphQLRequest,
    private val updaterHtmlClient: UpdaterHtmlClient
) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val THRESHOLD_START = System.getenv("GITHUB_THRESHOLD")?.toFloatOrNull() ?: 1000f
    private val THRESHOLD_HARD_FLOOR = System.getenv("GITHUB_THRESHOLD_HARD_FLOOR")?.toFloatOrNull() ?: 200f

    open suspend fun <E, F : HasRateLimit> getAll(
        requestEntityBuilder: (cursor: String?) -> GraphQLClientRequest<F>,

        extract: suspend (F) -> List<E>,
        hasNext: (F) -> Boolean,
        getCursor: (F) -> String?,

        initialCursor: String? = null,
        response: F? = null
    ): List<E> {
        var cursor = initialCursor

        if (response != null) {
            if (!hasNext(response)) {
                return listOf()
            } else {
                cursor = getCursor(response)
            }
        }

        val result = queryApi(requestEntityBuilder, cursor)

        if (result == null) {
            LOGGER.warn("No data returned")
            return emptyList()
        }

        if (repoDoesNotExist(result)) return listOf()

        selfRateLimit(result)

        if (result.data == null) {
            LOGGER.warn("No data returned")
            return emptyList()
        }

        val newData = extract(result.data!!)

        val more = getAll(requestEntityBuilder, extract, hasNext, getCursor, initialCursor, result.data)

        return newData.plus(more)
    }

    private fun <F : HasRateLimit> repoDoesNotExist(result: GraphQLClientResponse<F>): Boolean {
        if (result.errors?.isNotEmpty() == true) {
            if (result.errors?.any { it.message.contains("Could not resolve to a Repository") } == true) {
                return true
            }

            result.errors?.forEach {
                LOGGER.warn("Request failed $it.message")
            }
        }
        return false
    }

    private suspend fun <F : HasRateLimit> selfRateLimit(result: GraphQLClientResponse<F>) {
        if (result.data == null) {
            return
        }

        val rateLimitData = result.data!!.rateLimit
        if (rateLimitData.remaining < THRESHOLD_START) {
            var quota = getRemainingQuota()
            do {
                val delayTime = max(10, quota.second)
                LOGGER.debug("Remaining data getting low $quota ${rateLimitData.cost} $delayTime")
                delay(1000 * delayTime)

                quota = getRemainingQuota()
            } while (quota.first < THRESHOLD_START)
        }

        // Reduce log rate
        if ((Random.nextInt() % 10) == 0) {
            LOGGER.debug("RateLimit ${rateLimitData.remaining} ${rateLimitData.cost}")
        }
    }

    private suspend fun getRemainingQuota(): Pair<Int, Long> {
        try {
            val response = updaterHtmlClient.get("https://api.github.com/rate_limit")
            if (response != null) {
                return processResponse(response)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to read remaining quota", e)
        }
        return Pair(0, 100)
    }

    private fun processResponse(result: String): Pair<Int, Long> {
        val json = UpdaterJsonMapper.mapper.readValue(result, JsonObject::class.java)
        val remainingQuota = json.getJsonObject("resources")
            ?.getJsonObject("graphql")
            ?.getInt("remaining")
        val resetTime = json.getJsonObject("resources")
            ?.getJsonObject("graphql")
            ?.getJsonNumber("reset")?.longValue()

        if (resetTime != null && remainingQuota != null) {
            val delayTime = if (remainingQuota > THRESHOLD_HARD_FLOOR) {
                // scale delay, sleep for 1 second at rate limit == 1000
                // then scale up to 400 seconds at rate limit == 1
                (400f * (THRESHOLD_START - remainingQuota) / THRESHOLD_START).toLong()
            } else {
                val reset = LocalDateTime.ofEpochSecond(resetTime, 0, ZoneOffset.UTC)
                LOGGER.info("Remaining quota VERY LOW $remainingQuota delaying til $reset")
                ChronoUnit.SECONDS.between(TimeSource.now(), reset)
            }

            return Pair(remainingQuota, delayTime)
        } else {
            throw Exception("Unable to parse graphql data")
        }
    }

    open suspend fun <F : HasRateLimit> queryApi(
        requestEntityBuilder: (cursor: String?) -> GraphQLClientRequest<F>,
        cursor: String?
    ): GraphQLClientResponse<F>? {

        val query = requestEntityBuilder.invoke(cursor)

        if (APIConfig.DEBUG) {
            LOGGER.debug("GraphQL query: "+query.query)
        }

        var retryCount = 0
        while (retryCount <= 20) {
            try {
                val response = graphQLRequest.request(query)

                if (response.errors == null || response.errors?.isEmpty() == true) {
                    if (response.data == null) {
                        throw Exception("No data returned for query ${query.query}")
                    }
                    return response
                }

                if (repoDoesNotExist(response)) return response

                LOGGER.info("Failed query: "+query.query)
                LOGGER.info("Retrying ${retryCount++}")
                delay((TimeUnit.SECONDS.toMillis(5) * retryCount))
            } catch (e: ResponseException) {
                if (e.response.status == HttpStatusCode.Forbidden ||
                    e.response.status == HttpStatusCode.BadGateway ||
                    e.response.status == HttpStatusCode.ServiceUnavailable ||
                    e.response.status == HttpStatusCode.GatewayTimeout) {
                    // Normally get these due to tmp ban due to rate limiting
                    LOGGER.info("Retrying ${e.response.status} ${retryCount++}")
                    delay((TimeUnit.SECONDS.toMillis(5) * retryCount))
                } else {
                    printError(query, cursor)
                    throw Exception("Unexpected return type ${e.response.status}")
                }

            } catch (e: MismatchedInputException) {
                LOGGER.info("MismatchedInputException: "+e)
                return null
            } catch (e: Exception) {
                LOGGER.error("Query failed", e)
                throw e
            }
        }

        printError(query, cursor)
        throw Exception("Update hit retry limit")
    }

    private fun <F : HasRateLimit> printError(query: GraphQLClientRequest<F>, cursor: String?) {
        LOGGER.warn("Retry limit hit ${query.query}")
        LOGGER.warn("Cursor $cursor")
    }
}
