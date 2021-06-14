package net.adoptopenjdk.api.v3.dataSources.github

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UrlRequest
import net.adoptopenjdk.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptopenjdk.api.v3.dataSources.mongo.InternalDbStore
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

interface GitHubHtmlClient {
    suspend fun getUrl(url: String): String?
}

@Singleton
class CachedGitHubHtmlClient @Inject constructor(
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
