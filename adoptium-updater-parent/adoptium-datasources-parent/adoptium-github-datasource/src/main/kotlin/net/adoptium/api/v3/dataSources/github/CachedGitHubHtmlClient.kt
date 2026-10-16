package net.adoptium.api.v3.dataSources.github

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

interface GitHubHtmlClient {
    suspend fun getUrl(url: String): String?

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

    override suspend fun getAsset(asset: GHAsset): String? {
        // Never use the URL-only cache (or Last-Modified) for mutable release URLs.
        // Include both identity fields exactly; a timestamp is not an identity.
        val key = if (!asset.id.isNullOrBlank() || asset.sha256() != null) {
            "github-asset:${asset.id.orEmpty().length}:${asset.id.orEmpty()}:${asset.digest.orEmpty()}"
        } else null
        val expected = asset.sha256()
        fun valid(bytes: ByteArray) = expected == null ||
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == expected

        if (key != null) {
            internalDbStore.getCachedWebpage(key)?.data?.let { cached ->
                if (valid(cached.toByteArray(Charsets.UTF_8))) return cached
            }
        }
        for (attempt in 1..10) {
            try {
                val response = updaterHtmlClient.getFullResponse(UrlRequest(asset.downloadUrl))
                check(response?.statusLine?.statusCode == 200) { "Sidecar unavailable: ${asset.name}" }
                val bytes = response!!.entity.content.use { it.readBytes() }
                check(valid(bytes)) { "Sidecar digest mismatch: ${asset.name}" }
                val body = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
                if (key != null) internalDbStore.putCachedWebpage(key, null, TimeSource.now(), body).join()
                return body
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOGGER.warn("Failed to read sidecar $attempt ${asset.downloadUrl}", e)
                if (attempt < 10) delay(1000)
            }
        }
        return null
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

    private suspend fun get(request: UrlRequest): String? {
        // Retry up to 10 times
        for (retryCount in 1..10) {
            try {
                LOGGER.debug("Getting  ${request.url} ${request.lastModified}")
                val response = updaterHtmlClient.getFullResponse(request)

                if (response?.statusLine?.statusCode == 304) {
                    internalDbStore.updateCheckedTime(request.url, TimeSource.now())
                    // asset has not updated
                    return null
                }

                val body = DefaultUpdaterHtmlClient.extractBody(response)

                val lastModified = response?.getFirstHeader("Last-Modified")?.value

                internalDbStore.putCachedWebpage(request.url, lastModified, TimeSource.now(), body)
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
