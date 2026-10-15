package net.adoptium.api.v3.dataSources.github

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UrlRequest
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

interface GitHubHtmlClient {
    suspend fun getUrl(url: String): String?

    suspend fun getUrl(url: String, updatedAt: ZonedDateTime): String? = getUrl(url)

    suspend fun getAsset(asset: GHAsset): String? = getUrl(asset.downloadUrl)
}

@ApplicationScoped
open class CachedGitHubHtmlClient @Inject constructor(
    private val internalDbStore: InternalDbStore,
    private val updaterHtmlClient: UpdaterHtmlClient
) : GitHubHtmlClient {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        val LAST_MODIFIED_PARSER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)!!
    }

    private var backgroundHtmlDispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var refreshJob: Job

    // List of urls to be refreshed in the background
    private val workList = LinkedBlockingQueue<UrlRequest>()

    init {
        // Do refresh in the background
        refreshJob = GlobalScope.launch(backgroundHtmlDispatcher, block = cacheRefreshDaemonThread())
    }

    override suspend fun getUrl(url: String): String? {
        val cachedEntry = internalDbStore.getCachedWebpage(url)
        return if (cachedEntry == null) {
            get(UrlRequest(url))
        } else {
            if (shouldUpdate(cachedEntry)) {
                LOGGER.debug("Scheduling for refresh $url ${cachedEntry.lastModified} ${workList.size}")
                workList.offer(UrlRequest(url, cachedEntry.lastModified))
            }
            cachedEntry.data
        }
    }

    override suspend fun getUrl(url: String, updatedAt: ZonedDateTime): String? {
        val cachedEntry = internalDbStore.getCachedWebpage(url)
        val lastChecked = cachedEntry?.lastChecked
        if (cachedEntry == null || lastChecked == null || lastChecked.isBefore(updatedAt)) {
            // Rolling EA assets reuse URLs. Read the new contents before mapping the
            // release rather than returning stale data while refreshing in the background.
            return get(UrlRequest(url))
        }
        return getUrl(url)
    }

    override suspend fun getAsset(asset: GHAsset): String? {
        val cacheKey = if (asset.id != null || asset.digest != null) {
            "${asset.downloadUrl}#github-asset=${asset.id.orEmpty()};digest=${asset.digest.orEmpty()}"
        } else {
            // No reliable version token: do not trust a URL-only cached snapshot.
            return get(UrlRequest(asset.downloadUrl))
        }
        val cached = internalDbStore.getCachedWebpage(cacheKey)?.data
        return cached ?: get(UrlRequest(asset.downloadUrl), cacheKey, asset.digest)
    }

    private fun shouldUpdate(cachedEntry: CacheDbEntry): Boolean {
        val lastModified = if (cachedEntry.lastModified != null) {
            ZonedDateTime.parse(cachedEntry.lastModified, LAST_MODIFIED_PARSER)
        } else {
            TimeSource.now()
        }

        val lastChecked = cachedEntry.lastChecked ?: TimeSource.now().minusYears(20)

        val daysSinceModified = ChronoUnit.DAYS.between(lastModified, TimeSource.now())
        val daysSinceChecked = ChronoUnit.DAYS.between(lastChecked, TimeSource.now())

        // check if:
        // 1) Assets modified in the last 30 days refresh once a day
        // 2) Assets modified more than 30 days ago refresh once a week
        return daysSinceModified > 30 && daysSinceChecked >= 7 ||
            daysSinceModified <= 30 && daysSinceChecked >= 1
    }

    private fun cacheRefreshDaemonThread(): suspend CoroutineScope.() -> Unit {
        return {
            while (true) {
                val request = workList.take()
                LOGGER.debug("Enqueuing ${request.url} ${request.lastModified} ${workList.size}")
                get(request)
            }
        }
    }

    private suspend fun get(request: UrlRequest, cacheKey: String = request.url, digest: String? = null): String? {
        // Retry up to 10 times
        for (retryCount in 1..10) {
            try {
                LOGGER.debug("Getting  ${request.url} ${request.lastModified}")
                val response = updaterHtmlClient.getFullResponse(request)

                if (response?.statusLine?.statusCode == 304) {
                    check(request.lastModified != null) { "Unexpected 304 for an uncached asset" }
                    internalDbStore.updateCheckedTime(cacheKey, TimeSource.now())
                    // asset has not updated
                    return null
                }

                val body = if (digest != null && response != null) {
                    val bytes = response.entity.content.use { it.readBytes() }
                    val actualDigest = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                    check(actualDigest.equals(digest, ignoreCase = true)) { "Asset digest mismatch for ${request.url}" }
                    bytes.toString(Charsets.UTF_8)
                } else {
                    DefaultUpdaterHtmlClient.extractBody(response)
                } ?: return null

                val lastModified = response?.getFirstHeader("Last-Modified")?.value

                internalDbStore.putCachedWebpage(cacheKey, lastModified, TimeSource.now(), body)
                LOGGER.debug("Got ${request.url}")
                return body
            } catch (e: Exception) {
                LOGGER.error("Failed to read data retrying $retryCount ${request.url}", e)
                delay(1000)
            }
        }

        return null
    }

    fun getQueueLength(): Int {
        return workList.size
    }
}
