package net.adoptopenjdk.api.v3.dataSources.github.graphql.clients

import io.aexp.nodes.graphql.GraphQLRequestEntity
import io.aexp.nodes.graphql.GraphQLResponseEntity
import io.aexp.nodes.graphql.Variable
import io.aexp.nodes.graphql.exceptions.GraphQLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UpdaterJsonMapper
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.HasRateLimit
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.json.JsonObject
import kotlin.math.max

abstract class GraphQLGitHubInterface(
    private val graphQLRequest: GraphQLRequest,
    private val updaterHtmlClient: UpdaterHtmlClient
) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val THRESHOLD_START = System.getenv("GITHUB_THRESHOLD")?.toFloatOrNull() ?: 1000f
    private val THRESHOLD_HARD_FLOOR = System.getenv("GITHUB_THRESHOLD_HARD_FLOOR")?.toFloatOrNull() ?: 200f

    fun request(query: String): GraphQLRequestEntity.RequestBuilder {
        return GraphQLRequestFactoryImpl()
            .getRequestBuilder()
            .request(query.trimIndent().replace("\n", ""))
    }

    protected suspend fun <E, F : HasRateLimit> getAll(
        requestEntityBuilder: GraphQLRequestEntity.RequestBuilder,

        extract: suspend (F) -> List<E>,
        hasNext: (F) -> Boolean,
        getCursor: (F) -> String?,

        initialCursor: String? = null,
        response: F? = null,
        clazz: Class<F>
    ): List<E> {
        var cursor = initialCursor

        if (response != null) {
            if (!hasNext(response)) {
                return listOf()
            } else {
                cursor = getCursor(response)
            }
        }

        val result: GraphQLResponseEntity<F> = queryApi(requestEntityBuilder, cursor, clazz)

        if (repoDoesNotExist(result)) return listOf()

        selfRateLimit(result)

        val newData = extract(result.response)

        val more = getAll(requestEntityBuilder, extract, hasNext, getCursor, initialCursor, result.response, clazz)

        return newData.plus(more)
    }

    private fun <F : HasRateLimit> repoDoesNotExist(result: GraphQLResponseEntity<F>): Boolean {
        if (result.errors != null && result.errors.isNotEmpty()) {
            if (result.errors[0].message.contains("Could not resolve to a Repository")) {
                return true
            }

            result.errors.forEach {
                LOGGER.warn(it.message)
            }
        }
        return false
    }

    private suspend fun <F : HasRateLimit> selfRateLimit(result: GraphQLResponseEntity<F>) {
        val rateLimitData = result.response.rateLimit
        if (rateLimitData.remaining < THRESHOLD_START) {
            var quota = getRemainingQuota()
            do {
                val delayTime = max(10, quota.second)
                LOGGER.debug("Remaining data getting low $quota ${rateLimitData.cost} $delayTime")
                delay(1000 * delayTime)

                quota = getRemainingQuota()
            } while (quota.first < THRESHOLD_START)
        }
        LOGGER.debug("RateLimit ${rateLimitData.remaining} ${rateLimitData.cost}")
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

    protected suspend fun <F : HasRateLimit> queryApi(
        requestEntityBuilder: GraphQLRequestEntity.RequestBuilder,
        cursor: String?,
        clazz: Class<F>
    ): GraphQLResponseEntity<F> {

        requestEntityBuilder.variables(Variable("cursorPointer", cursor))
        val query = requestEntityBuilder.build()

        var retryCount = 0
        while (retryCount <= 20) {
            try {
                return withContext(Dispatchers.Default) {
                    return@withContext graphQLRequest.query(query, clazz)
                }
            } catch (e: GraphQLException) {
                if (e.status == "403" || e.status == "502") {
                    // Normally get these due to tmp ban due to rate limiting
                    LOGGER.info("Retrying ${e.status} ${retryCount++}")
                    delay((TimeUnit.SECONDS.toMillis(5) * retryCount))
                } else {
                    printError(query, cursor)
                    throw Exception("Unexpected return type ${e.status}")
                }
            } catch (e: Exception) {
                LOGGER.error("Query failed", e)
                throw e
            }
        }

        printError(query, cursor)
        throw Exception("Update hit retry limit")
    }

    private fun printError(query: GraphQLRequestEntity?, cursor: String?) {
        LOGGER.warn("Retry limit hit $query")
        LOGGER.warn("Cursor $cursor")
    }
}
