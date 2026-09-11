package net.adoptium.api.v3.checker

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
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

class StagingLiveChecker(
    val stagingUrl: String,
    val liveUrl: String,
    val vendor: String
) {
    companion object {

        const val STAGING_URL = "https://staging-api.adoptium.net"
        const val LIVE_URL = "https://api.adoptium.net"

        const val STAGING_ADOPTOPENJDK_URL = "https://staging-api.adoptopenjdk.net"
        const val LIVE_ADOPTOPENJDK_URL = "https://api.adoptopenjdk.net"

        val TEMPLATED: Array<String> = arrayOf(

            "/v3/assets/feature_releases/{version}/ea",
            "/v3/assets/feature_releases/{version}/ga",
            "/v3/assets/feature_releases/{version}/ea?architecture=x64",
            "/v3/assets/feature_releases/{version}/ea?before=2020-12-21T10:15:30Z",
            "/v3/assets/feature_releases/{version}/ea?heap_size=large",
            "/v3/assets/feature_releases/{version}/ea?image_type=jdk",
            "/v3/assets/feature_releases/{version}/ea?jvm_impl=hotspot",
            "/v3/assets/feature_releases/{version}/ea?jvm_impl=openj9",
            "/v3/assets/feature_releases/{version}/ea?os=linux",
            "/v3/assets/feature_releases/{version}/ea&page=1",
            "/v3/assets/feature_releases/{version}/ea&page=1",
            "/v3/assets/feature_releases/{version}/ea?page=1",
            "/v3/assets/feature_releases/{version}/ea?page=2",
            "/v3/assets/feature_releases/{version}/ea?sort_method=DATE",
            "/v3/assets/feature_releases/{version}/ea?sort_order=ASC",
            "/v3/assets/feature_releases/{version}/ea?vendor=openjdk",
            "/v3/assets/feature_releases/{version}/ga?jvm_impl=hotspot",
            "/v3/assets/feature_releases/{version}/ga&page=1",
            "/v3/assets/feature_releases/{version}/ga&page=1",
            "/v3/assets/feature_releases/{version}/ga?page=1",
            "/v3/assets/feature_releases/{version}/ea?vendor=eclipse",

            "/v3/assets/latest/{version}/hotspot",
            "/v3/assets/latest/{version}/openj9",

            "/v3/binary/latest/{version}/ea/linux/x64/jdk/hotspot/normal/adoptium",
            "/v3/binary/latest/{version}/ea/linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ea/linux/x64/jdk/hotspot/normal/eclipse",
            "/v3/binary/latest/{version}/ea/linux/x64/jdk/hotspot/normal/ibm",
            "/v3/binary/latest/{version}/ea/mac/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ea/windows/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/aix/ppc64/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/alpine-linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/aarch64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/aarch64/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/arm/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/ppc64le/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/ppc64le/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/s390x/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/s390x/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/linux/x64/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/mac/aarch64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/mac/aarch64/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/mac/x64/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/windows/x32/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/windows/x32/jre/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/windows/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/windows/x64/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/latest/{version}/ga/windows/x64/jre/openj9/normal/adoptopenjdk",


            "/v3/installer/latest/{version}/ea/linux/s390x/jre/openj9/large/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/mac/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/mac/x64/jre/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x32/jdk/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x32/jre/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x64/jdk/hotspot/normal/openjdk",
            "/v3/installer/latest/{version}/ga/windows/x64/jre/hotspot/large/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x64/jre/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x86/jdk/hotspot/normal/adoptopenjdk",
            "/v3/installer/latest/{version}/ga/windows/x86/jre/hotspot/normal/adoptopenjdk",

            "/v3/assets/version/(,{version}]",
            "/v3/assets/version/({version},{version+1}]",
            "/v3/assets/version/({version},{version+1})?image_type=sbom&release_type=ea",
            "/v3/assets/version/[{version},{version+1})",
            "/v3/assets/version/({version},{version+1})?architecture=x64",
            "/v3/assets/version/({version},{version+1})?heap_size=large",
            "/v3/assets/version/({version},{version+1})?image_type=jdk",
            "/v3/assets/version/({version},{version+1})?jvm_impl=openj9",
            "/v3/assets/version/({version},{version+1})?lts=true",
            "/v3/assets/version/({version},{version+1})?os=linux",
            "/v3/assets/version/({version},{version+1})?page=2",
            "/v3/assets/version/({version},{version+1})?page_size=2",
            "/v3/assets/version/({version},{version+1})?project=jdk",
            "/v3/assets/version/({version},{version+1})?release_type=ea",
            "/v3/assets/version/({version},{version+1})?sort_method=DATE",
            "/v3/assets/version/({version},{version+1})?sort_order=ASC",
            "/v3/assets/version/({version},{version+1})?vendor=openjdk",
        )

        val RANGE_OF_CALLS: Array<String> = arrayOf(

            "/v3/assets/version/[1.0,100.0]",
            "/v3/assets/version/[1.0,100.0]?jvm_impl=hotspot",
            "/v3/assets/version/(10,11)",
            "/v3/assets/version/[11,)",
            "/v3/assets/version/11.0.10+9",
            "/v3/assets/version/(11.0,12.0)",
            "/v3/assets/version/11.0.2+9",
            "/v3/assets/version/[11.0.5,11.0.6)",
            "/v3/assets/version/11.0.8+10",
            "/v3/assets/version/(11,12)",
            "/v3/assets/version/[11,12)",
            "/v3/assets/version/(11,12)?architecture=x64",
            "/v3/assets/version/(11,12)?heap_size=large",
            "/v3/assets/version/(11,12)?image_type=jdk",
            "/v3/assets/version/(11,12)?image_type=&release_type=ea",
            "/v3/assets/version/(11,12)?image_type=sbom&release_type=ea",
            "/v3/assets/version/(11,12)?jvm_impl=openj9",
            "/v3/assets/version/(11,12)?lts=true",
            "/v3/assets/version/(11,12)?os=linux",
            "/v3/assets/version/(11,12)?page=2",
            "/v3/assets/version/(11,12)?page_size=2",
            "/v3/assets/version/(11,12)?project=jdk",
            "/v3/assets/version/(11,12)?release_type=ea",
            "/v3/assets/version/(11,12)?sort_method=DATE",
            "/v3/assets/version/(11,12)?sort_order=ASC",
            "/v3/assets/version/(11,12)?vendor=openjdk",
            "/v3/assets/version/(11,99)",
            "/v3/assets/version/(11,99)?architecture=x64",
            "/v3/assets/version/(11,99)?heap_size=large",
            "/v3/assets/version/(11,99)?image_type=jdk",
            "/v3/assets/version/(11,99)?jvm_impl=openj9",
            "/v3/assets/version/(11,99)?lts=true",
            "/v3/assets/version/(11,99)?os=linux",
            "/v3/assets/version/(11,99)?page=2",
            "/v3/assets/version/(11,99)?page_size=2",
            "/v3/assets/version/(11,99)?project=jdk",
            "/v3/assets/version/(11,99)?release_type=ea",
            "/v3/assets/version/(11,99)?sort_method=DATE",
            "/v3/assets/version/(11,99)?sort_order=ASC",
            "/v3/assets/version/(11,99)?vendor=openjdk",
            "/v3/assets/version/(12,13)",
            "/v3/assets/version/[12,13)",
            "/v3/assets/version/(13,14)",
            "/v3/assets/version/[13,14)",
            "/v3/assets/version/(14,15)",
            "/v3/assets/version/[14,15)",
            "/v3/assets/version/(15,16)",
            "/v3/assets/version/[15,16)",
            "/v3/assets/version/[15,99)",
            "/v3/assets/version/(1.8.0,9)",
            "/v3/assets/version/[8,)",
            "/v3/assets/version/8.0.212+4",
            "/v3/assets/version/(8,12]",
            "/v3/assets/version/(8,18)?image_type=sbom&release_type=ea",
            "/v3/assets/version/[8,9)",
            "/v3/assets/version/(,9.0]",


            "/v3/binary/version/jdk-11.0.11+9_openj9-0.26.0/linux/x64/jdk/openj9/normal/adoptopenjdk",
            "/v3/binary/version/jdk-11.0.17+8/linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/version/jdk-11.0.17+8/linux/x64/jre/hotspot/normal/adoptopenjdk",
            "/v3/binary/version/jdk-15.0.1+9/linux/aarch64/jdk/hotspot/normal/adoptopenjdk.sha1",
            "/v3/binary/version/jdk-15.0.1+9/linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/version/jdk-15.0.1+9/linux/x64/jdk/hotspot/normal/adoptopenjdk.sha1",
            "/v3/binary/version/jdk-15.0.1+9/mac/x64/jdk/hotspot/normal/adoptopenjdk.sha1",
            "/v3/binary/version/jdk-15.0.1+9/windows/x64/jdk/hotspot/normal/adoptopenjdk.sha1",
            "/v3/binary/version/jdk-17+35/linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/version/jdk-457889/windows/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/version/jdk8u242-b08/linux/x64/jdk/hotspot/normal/adoptopenjdk",
            "/v3/binary/version/jdk8u242-b08/linux/x64/jdk/hotspot/normal/adoptopenjdk.sha1",
            "/v3/binary/version/jdk8u242-b08/windows/x64/jdk/hotspot/normal/adoptopenjdk.sha1",
            "/v3/binary/version/jdk8u292-b10/linux/x64/jdk/hotspot/normal/adoptopenjdk",


            "/v3/info/available_releases",
            "/v3/info/release_names",
            "/v3/info/release_versions",


            "/v3/version/jdk-11.0.6+10",
            "/v3/version/jdk8u212-b04",
        )

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.contains("--help") || args.contains("-h")) {
                println("Usage: StagingLiveChecker [vendor] [options]")
                println()
                println("Vendor (first positional argument):")
                println("  adoptium       Use Adoptium staging/live URLs (default)")
                println("  adoptopenjdk   Use AdoptOpenJDK staging/live URLs")
                println()
                println("Options:")
                println("  --staging-url <url>    Override the staging API base URL")
                println("  --live-url <url>       Override the live API base URL")
                println("  --versions <v1,v2,...>  Comma-separated list of Java versions to check")
                println("                         (default: 8,11,17,21,22,23,24,25)")
                println("  --help, -h             Show this help message and exit")
                println()
                println("Examples:")
                println("  StagingLiveChecker")
                println("  StagingLiveChecker adoptopenjdk")
                println("  StagingLiveChecker adoptium --versions 11,17,21")
                println("  StagingLiveChecker --staging-url https://my-staging.example.com --live-url https://my-live.example.com")
                kotlin.system.exitProcess(0)
            }

            val argsList = args.toMutableList()
            val vendor = if (argsList.isNotEmpty() && argsList[0] == "adoptopenjdk") {
                argsList.removeAt(0)
                "adoptopenjdk"
            } else {
                if (argsList.isNotEmpty() && argsList[0] == "adoptium") {
                    argsList.removeAt(0)
                }
                "adoptium"
            }

            var stagingUrl: String? = null
            var liveUrl: String? = null
            var versions: IntArray? = null
            val remaining = mutableListOf<String>()

            val iter = argsList.iterator()
            while (iter.hasNext()) {
                val arg = iter.next()
                when {
                    arg == "--staging-url" -> stagingUrl = if (iter.hasNext()) iter.next() else error("--staging-url requires a value")
                    arg == "--live-url" -> liveUrl = if (iter.hasNext()) iter.next() else error("--live-url requires a value")
                    arg == "--versions" -> versions = if (iter.hasNext()) iter.next().split(",").map { it.trim().toInt() }.toIntArray() else error("--versions requires a value")
                    else -> remaining.add(arg)
                }
            }

            val resolvedStagingUrl = stagingUrl ?: if (vendor == "adoptopenjdk") STAGING_ADOPTOPENJDK_URL else STAGING_URL
            val resolvedLiveUrl = liveUrl ?: if (vendor == "adoptopenjdk") LIVE_ADOPTOPENJDK_URL else LIVE_URL

            val resolvedVersions = versions ?: if (remaining.isNotEmpty()) {
                remaining.map { it.toInt() }.toIntArray()
            } else {
                intArrayOf(8, 11, 17, 21, 22, 23, 24, 25)
            }

            val failures = runBlocking {
                StagingLiveChecker(resolvedStagingUrl, resolvedLiveUrl, vendor).compareAll(resolvedVersions)
            }
            println()
            println("==== Summary ====")
            if (failures.isEmpty()) {
                println("StagingLiveChecker completed successfully. All URLs passed.")
                kotlin.system.exitProcess(0)
            } else {
                println("StagingLiveChecker completed with ${failures.size} failure(s):")
                failures.forEach { println("  FAILED: $it") }
                kotlin.system.exitProcess(1)
            }
        }
    }

    suspend fun compareAll(versions: IntArray = intArrayOf(8, 11, 17, 21, 22, 23, 24, 25)): List<String> {
        val failures = mutableListOf<String>()

        versions.forEach { version ->
            TEMPLATED.forEach { url ->
                val resolvedUrl = url
                    .replace("{version}", version.toString())
                    .replace("{version+1}", (version + 1).toString())
                val result = compare(resolvedUrl)
                if (result != null) failures.add(result)
            }
        }

        RANGE_OF_CALLS
            .forEach { url ->
                val result = compare(url)
                if (result != null) failures.add(result)
            }

        return failures
    }

    private suspend fun StagingLiveChecker.compare(url: String): String? {
        try {
            val l = formUrl(liveUrl, url)

            val redirect = !l.contains("binary") && !l.contains("installer")

            val staging = get(URL(formUrl(stagingUrl, url)), redirect)
            val live = get(URL(formUrl(liveUrl, url)), redirect)

            when {
                live.statusLine.statusCode != staging.statusLine.statusCode -> {
                    println("Bad code $url ${live.statusLine.statusCode} ${staging.statusLine.statusCode}")
                    return "$url (status code mismatch: live=${live.statusLine.statusCode}, staging=${staging.statusLine.statusCode})"
                }

                live.statusLine.statusCode == 200 -> {
                    return compareJsonData(live, staging, url, formUrl(stagingUrl, url), formUrl(liveUrl, url))
                }

                live.statusLine.statusCode == 307 -> {
                    val liveLocation = live.getHeaders("location")[0].getValue()
                    val stagingLocation = staging.getHeaders("location")[0].getValue()
                    if (liveLocation != stagingLocation) {
                        println("Different redirect:  $liveLocation $stagingLocation")
                        return "$url (different redirect: live=$liveLocation, staging=$stagingLocation)"
                    } else {
                        println("good $url ${live.statusLine.statusCode}")
                    }
                }

                else -> {
                    println("good $url ${live.statusLine.statusCode}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "$url (exception: ${e.message})"
        }
        return null
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
        val query = url.substring(paramsIndex)

        val query2 = if (query.contains("vendor")) {
            query
        } else if (query.isEmpty()) {
            "?vendor=${vendor}"
        } else {
            "$query&vendor=${vendor}"
        }

        val urlEncoded = URLEncoder.encode(suffix, "UTF-8")

        val url2 = if (host.contains("adoptium") && urlEncoded.contains("adoptopenjdk")) {
            urlEncoded.replace("adoptopenjdk", "eclipse")
        } else {
            urlEncoded
        }
        val normalizedHost = host.removeSuffix("/")
        return "$normalizedHost$prefix$url2$query2"
    }

    private fun compareJsonData(
        live: HttpResponse,
        staging: HttpResponse,
        url: String,
        stagingUrl: String,
        liveUrl: String
    ): String? {
        val liveJsonCleaned = getData(live)
        val stagingJsonCleaned = getData(staging)

        removeOutOfSyncData(liveJsonCleaned, stagingJsonCleaned)

        try {
            val result: JSONCompareResult = JSONCompare.compareJSON(
                liveJsonCleaned?.toString(),
                stagingJsonCleaned?.toString(),
                JSONCompareMode.NON_EXTENSIBLE
            )
            if (result.failed()) {
                println("====================")
                println("Failed url curl -L -o - \"$stagingUrl\" | grep -v download_count > /tmp/a && curl -L -o - \"$liveUrl\" | grep -v download_count > /tmp/b && diff /tmp/a /tmp/b")
                println(result.message)
                println("====================")
                return "$url (JSON mismatch)"
            } else {
                println("good $url ${live.statusLine.statusCode}")
            }
        } catch (e: Exception) {
            println("Exception comparing JSON for $url: ${e.message}")
            return "$url (JSON comparison exception: ${e.message})"
        }
        return null
    }

    private fun removeOutOfSyncData(
        liveJsonCleaned: JsonElement?,
        stagingJsonCleaned: JsonElement?
    ) {
        // if array sizes differ it is likely that staging and live are slightly out of sync, remove elements that are out of sync
        if (liveJsonCleaned?.isJsonArray == true && stagingJsonCleaned?.isJsonArray == true) {
            while (liveJsonCleaned.asJsonArray.size() > 0 && stagingJsonCleaned.asJsonArray.size() > 0) {
                // probably due to updates out of sync, pop off the newer entries
                val liveId = liveJsonCleaned.asJsonArray?.get(0)?.asJsonObject?.get("id")
                val stagingId = stagingJsonCleaned.asJsonArray?.get(0)?.asJsonObject?.get("id")

                if (liveId != stagingId) {
                    stagingJsonCleaned.asJsonArray?.remove(0)
                    liveJsonCleaned.asJsonArray?.size()?.minus(1)?.let { liveJsonCleaned.asJsonArray?.remove(it) }
                } else {
                    break
                }
            }
        }
    }

    private fun getData(live: HttpResponse): JsonElement? {
        val liveBody = DefaultUpdaterHtmlClient.extractBody(live)
        var liveJson = JsonParser.parseString(liveBody)

        // Ignore download counts as they will probably always differ
        liveJson = removeFieldElement(liveJson) { key, value ->
            key == "download_count" ||
                key == "aqavit_results_link" ||
                value.isJsonObject &&
                value.asJsonObject.getAsJsonPrimitive("vendor")?.asString == "adoptium" ||
                key == "release_notes"
        }
        return liveJson
    }

    private fun removeFieldElement(jsonValue: JsonElement?, predicate: (String?, JsonElement) -> Boolean): JsonElement? {
        return when {
            jsonValue == null -> {
                null
            }

            jsonValue.isJsonNull -> {
                jsonValue.asJsonNull
            }

            jsonValue.isJsonObject -> {
                removeField(jsonValue.asJsonObject, predicate)
            }

            jsonValue.isJsonArray -> {
                removeField(jsonValue.asJsonArray, predicate)
            }

            else -> {
                jsonValue.asJsonPrimitive
            }
        }
    }

    private fun removeField(jsonObject: JsonObject?, predicate: (String?, JsonElement) -> Boolean): JsonElement? {
        if (jsonObject == null) return null

        val result = JsonObject()

        if (jsonObject.has("dateTime")) {
            return JsonPrimitive(jsonObject.get("dateTime").asString)
        }

        jsonObject
            .entrySet()
            .filter { !predicate(it.key, it.value) }
            .forEach {
                val v = removeFieldElement(it.value, predicate)
                if (v != null) result.add(it.key, v)
            }

        return result
    }

    private fun removeField(array: JsonArray?, predicate: (String?, JsonElement) -> Boolean): JsonArray? {
        if (array == null) return null

        val jsonArray = JsonArray()

        array
            .filter { !predicate(null, it) }
            .forEach {
                jsonArray.add(removeFieldElement(it, predicate))
            }

        if (jsonArray.size() == 0) {
            return null
        }

        return jsonArray
    }

    private fun removeFieldElement(s: String, jsonValue: JsonElement?): JsonElement? {
        return when {
            jsonValue == null -> {
                null
            }

            jsonValue.isJsonNull -> {
                jsonValue.asJsonNull
            }

            jsonValue.isJsonObject -> {
                removeField(s, jsonValue.asJsonObject)
            }

            jsonValue.isJsonArray -> {
                removeField(s, jsonValue.asJsonArray)
            }

            else -> {
                jsonValue.asJsonPrimitive
            }
        }
    }

    private fun removeField(s: String, jsonObject: JsonObject?): JsonElement? {
        if (jsonObject == null) return null

        val result = JsonObject()

        if (jsonObject.has("dateTime")) {
            return JsonPrimitive(jsonObject.get("dateTime").asString)
        }

        jsonObject
            .entrySet()
            .filter { it.key != s }
            .forEach {
                result.add(it.key, removeFieldElement(s, it.value))
            }

        return result
    }

    private fun removeField(s: String, array: JsonArray?): JsonArray? {
        if (array == null) return null

        val jsonArray = JsonArray()

        array.forEach {
            jsonArray.add(removeFieldElement(s, it))
        }

        return jsonArray
    }

    private val httpClient = HttpClientFactory().getNonRedirectHttpClient()
    private val httpClientRedirect = HttpClientFactory().getHttpClient()

    private suspend fun get(url: URL, redirect: Boolean = false): HttpResponse {
        val request = org.apache.http.client.methods.RequestBuilder
            .get(url.toURI())
            .setConfig(HttpClientFactory.REQUEST_CONFIG)
            .build()

        var client = if (redirect) httpClientRedirect else httpClient

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
                        continuation.resumeWith(Result.failure(java.util.concurrent.CancellationException("Request cancelled")))
                    }
                }
            )
        }
    }
}
