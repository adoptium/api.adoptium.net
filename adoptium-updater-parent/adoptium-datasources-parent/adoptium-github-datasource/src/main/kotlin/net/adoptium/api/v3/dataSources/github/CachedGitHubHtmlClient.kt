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
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

interface GitHubHtmlClient {
    suspend fun getUrl(url: String): String?

    /**
     * Like [getUrl], but guarantees the result is not older than [updatedAt] (the time the remote asset was
     * last replaced), revalidating a cached copy synchronously if it was last checked before that time.
     */
    suspend fun getUrlUpdatedSince(url: String, updatedAt: ZonedDateTime?): String? = getUrl(url)
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

    private var backgroundHtmlDispatcher: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor {
        Thread(it, "cached-github-html-client").apply { isDaemon = true }
    }.asCoroutineDispatcher()
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

    override suspend fun getUrlUpdatedSince(url: String, updatedAt: ZonedDateTime?): String? {
        val cachedEntry = internalDbStore.getCachedWebpage(url)
        if (cachedEntry == null) {
            return getUrl(url)
        }

        if (cachedEntry.data == null) {
            // A null body is a failed fetch (404 or exhausted retries), not a valid cached response, so its
            // lastChecked says nothing about freshness. Retry now, unconditionally (there is no body to validate).
            return get(UrlRequest(url))
        }

        if (updatedAt == null) {
            return getUrl(url)
        }

        val lastChecked = cachedEntry.lastChecked
        if (lastChecked != null && !lastChecked.isBefore(updatedAt)) {
            // Cached copy was validated after the asset was last updated
            return getUrl(url)
        }

        // Asset changed since we last checked it, revalidate now rather than in the background.
        // get() returns null on HTTP 304 (unchanged) or failure, in which case the cached body is still the best we have.
        return get(UrlRequest(url, cachedEntry.lastModified)) ?: cachedEntry.data
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
                get(request, accessedAt = null)
            }
        }
    }

    private suspend fun get(request: UrlRequest, accessedAt: ZonedDateTime? = TimeSource.now()): String? {
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

                internalDbStore.putCachedWebpage(request.url, lastModified, TimeSource.now(), body, accessedAt).join()
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
