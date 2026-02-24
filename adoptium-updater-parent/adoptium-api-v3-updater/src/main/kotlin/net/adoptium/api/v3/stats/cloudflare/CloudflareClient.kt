package net.adoptium.api.v3.stats.cloudflare

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
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@ApplicationScoped
class CloudflareClient @Inject constructor(
    @Named(HttpClientFactory.REDIRECTING)
    private val httpClient: HttpAsyncClient
) {
    companion object {
        const val CLOUDFLARE_GRAPHQL_URL = "https://api.cloudflare.com/client/v4/graphql"
    }

    // FIXME: Is it ok to read this from env vars?
    private val apiToken: String = APIConfig.ENVIRONMENT["CLOUDFLARE_API_TOKEN"] ?: throw IllegalStateException("CLOUDFLARE_API_TOKEN not set")
    private val zoneTag: String = APIConfig.ENVIRONMENT["CLOUDFLARE_ZONE_TAG"] ?: throw IllegalStateException("CLOUDFLARE_ZONE_TAG not set")

    suspend fun fetchDownloadStats(startDate: ZonedDateTime, endDate: ZonedDateTime): CloudflareResponse {
        val query = buildGraphQLQuery(startDate, endDate)
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

        if (statusCode != 200) {
            throw Exception("CloudFlare API returned status code: $statusCode")
        }

        val body = response.entity.content.bufferedReader().readText()
        return CloudflareResponse.fromJson(body)
    }

    private fun buildGraphQLQuery(startDate: ZonedDateTime, endDate: ZonedDateTime): String {
        val start = startDate.toLocalDate().toString()
        val end = endDate.toLocalDate().toString()

        return """
        {
            "query": "query {
                viewer {
                    zones(filter: { zoneTag: \"$zoneTag\" }) {
                        httpRequestsAdaptiveGroups(
                            limit: 1000,
                            orderBy: [date_ASC],
                            filter: {
                                date_geq: \"$start\",
                                date_leq: \"$end\",
                                clientRequestHTTPHost: \"packages.adoptium.net\",
                                OR: [
                                    {
                                        AND: [
                                            {clientRequestPath_like: \"%.deb\"},
                                            {clientRequestPath_notlike: \"%/adoptium-ca-certificates%\"}
                                        ]
                                    },
                                    {clientRequestPath_like: \"%.rpm\"},
                                    {clientRequestPath_like: \"%.apk\"}
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
            }"
        }
        """.trimIndent()
    }
}
