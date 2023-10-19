package net.adoptium.marketplace.checker

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptium.api.v3.dataSources.HttpClientFactory
import org.apache.http.HttpResponse
import org.apache.http.concurrent.FutureCallback
import org.skyscreamer.jsonassert.JSONCompare
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.JSONCompareResult
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess

class StagingLiveChecker(
    val stagingUrl: String,
    val liveUrl: String
) {
    companion object {

        const val STAGING_URL = "https://staging-marketplace-api.adoptium.net/"
        const val LIVE_URL = "https://marketplace-api.adoptium.net/"

        val RANGE_OF_CALLS: Array<String> = arrayOf(
            "/v1/assets/feature_releases/adoptium/11",
            "/v1/assets/feature_releases/adoptium/11/",
            "/v1/assets/feature_releases/adoptium/12",
            "/v1/assets/feature_releases/adoptium/13",
            "/v1/assets/feature_releases/adoptium/17/",
            "/v1/assets/feature_releases/adoptium/20",
            "/v1/assets/feature_releases/adoptium/21",
            "/v1/assets/feature_releases/adoptium/21/",
            "/v1/assets/feature_releases/adoptium/8",
            "/v1/assets/feature_releases/adoptium/8/",
            "/v1/assets/feature_releases/alibaba/11/",
            "/v1/assets/feature_releases/alibaba/13",
            "/v1/assets/feature_releases/alibaba/17/",
            "/v1/assets/feature_releases/alibaba/21/",
            "/v1/assets/feature_releases/alibaba/8/",
            "/v1/assets/feature_releases/azul/11/",
            "/v1/assets/feature_releases/azul/17/",
            "/v1/assets/feature_releases/azul/21",
            "/v1/assets/feature_releases/azul/21/",
            "/v1/assets/feature_releases/azul/8/",
            "/v1/assets/feature_releases/huawei/11/",
            "/v1/assets/feature_releases/huawei/17/",
            "/v1/assets/feature_releases/huawei/21/",
            "/v1/assets/feature_releases/huawei/8/",
            "/v1/assets/feature_releases/ibm/11/",
            "/v1/assets/feature_releases/ibm/13",
            "/v1/assets/feature_releases/ibm/17/",
            "/v1/assets/feature_releases/ibm/21/",
            "/v1/assets/feature_releases/ibm/8/",
            "/v1/assets/feature_releases/microsoft/11/",
            "/v1/assets/feature_releases/microsoft/17/",
            "/v1/assets/feature_releases/microsoft/21",
            "/v1/assets/feature_releases/microsoft/21/",
            "/v1/assets/feature_releases/microsoft/8/",
            "/v1/assets/feature_releases/redhat/11",
            "/v1/assets/feature_releases/redhat/11/",
            "/v1/assets/feature_releases/redhat/17/",
            "/v1/assets/feature_releases/redhat/21",
            "/v1/assets/feature_releases/redhat/21/",
            "/v1/assets/feature_releases/redhat/8/",
            "/v1/assets/latestForVendors",
            "/v1/assets/release_name/microsoft/jdk-17.0.8.1+1",
            "/v1/assets/version/adoptium/21",
            "/v1/info/available_releases/adoptium",
            "/v1/info/release_names/adoptium",
            "/v1/info/release_versions/adoptium",
            "/v1/info/release_versions/microsoft",
        )

        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                StagingLiveChecker(STAGING_URL, LIVE_URL).compareAll()
            }
            exitProcess(0)
        }
    }

    fun compareAll() {
        RANGE_OF_CALLS
            .forEach { url ->
                try {
                    runBlocking {
                        val liveUrlFull = formUrl(liveUrl, url)
                        val stagingUrlFull = formUrl(liveUrl, url)

                        val redirect = !liveUrl.contains("binary")
                        val staging = get(URL(stagingUrlFull), redirect)
                        val live = get(URL(liveUrlFull), redirect)
                        println(liveUrlFull)
                        when {
                            live.statusLine.statusCode != staging.statusLine.statusCode -> {
                                println("Bad code $url")
                            }

                            live.statusLine.statusCode == 200 -> {
                                compareJsonData(live, staging, url, stagingUrlFull, liveUrlFull)
                            }

                            live.statusLine.statusCode == 307 -> {
                                val liveLocation = live.getHeaders("location")[0].getValue()
                                val stagingLocation = staging.getHeaders("location")[0].getValue()
                                if (liveLocation != stagingLocation) {
                                    println("Different redirect:  $liveLocation $stagingLocation")
                                } else {
                                    println("good $url ${live.statusLine.statusCode}")
                                }
                            }

                            else -> {
                                println("good $url ${live.statusLine.statusCode}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }

    private fun formUrl(host: String, url: String): String {
        val finalSection = url.indexOfLast { c -> c == '/' } + 1

        val paramsIndex = if (url.contains('?')) {
            url.indexOfLast { c -> c == '?' }
        } else {
            url.length
        }

        val prefix = url.substring(0, finalSection)
        val suffix = url.substring(finalSection, paramsIndex)

        val urlEncoded = URLEncoder.encode(suffix, "UTF-8")

        return "$host$prefix$urlEncoded"
    }

    private fun compareJsonData(
        live: HttpResponse,
        staging: HttpResponse,
        url: String,
        stagingUrl: String,
        liveUrl: String
    ) {
        val liveJsonSanetised = getData(live)
        val stagingJsonSanetised = getData(staging)

        try {
            val result: JSONCompareResult = JSONCompare.compareJSON(
                liveJsonSanetised?.toString(),
                stagingJsonSanetised?.toString(),
                JSONCompareMode.NON_EXTENSIBLE
            )
            if (result.failed()) {
                println("====================")
                println("Failed url curl -L -o - \"$stagingUrl\" | grep -v download_count > /tmp/a && curl -L -o - \"$liveUrl\" | grep -v download_count > /tmp/b && meld /tmp/a /tmp/b")
                println(result.message)
                println("====================")
                liveJsonSanetised?.toString()
                stagingJsonSanetised?.toString()
            } else {
                println("good $url ${live.statusLine.statusCode}")
            }
        } catch (e: Exception) {
            e.toString()
        }
    }

    private fun getData(live: HttpResponse): JsonElement? {
        val liveBody = DefaultUpdaterHtmlClient.extractBody(live)
        return JsonParser.parseString(liveBody)
    }


    private val httpClient = HttpClientFactory().getNonRedirectHttpClient()
    private val httpClientRedirect = HttpClientFactory().getHttpClient()

    private suspend fun get(url: URL, redirect: Boolean = false): HttpResponse {
        val request = org.apache.http.client.methods.RequestBuilder
            .get(url.toURI())
            .setConfig(HttpClientFactory.REQUEST_CONFIG)
            .build()

        val client = if (redirect) httpClientRedirect else httpClient

        return suspendCoroutine { continuation ->
            client.execute(
                request,
                object : FutureCallback<HttpResponse> {
                    override fun completed(p0: HttpResponse) {
                        continuation.resumeWith(Result.success(p0))
                    }

                    override fun failed(p0: Exception) {
                        continuation.resumeWith(Result.failure(p0))
                    }

                    override fun cancelled() {
                        TODO("Not yet implemented")
                    }
                }
            )
        }
    }
}
