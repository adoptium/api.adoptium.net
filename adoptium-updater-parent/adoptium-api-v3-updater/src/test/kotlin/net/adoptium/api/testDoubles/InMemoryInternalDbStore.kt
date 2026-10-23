package net.adoptium.api.testDoubles

import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import kotlinx.coroutines.Job
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import java.time.ZonedDateTime

@Priority(1)
@Alternative
@ApplicationScoped
class InMemoryInternalDbStore : InternalDbStore {
    private val cache: MutableMap<String, CacheDbEntry> = HashMap()
    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime?): Job {
        synchronized(cache) {
            val previous = cache[url]?.lastAccessed
            val usage = if (previous == null) accessedAt ?: TimeSource.now()
                else if (accessedAt != null && accessedAt.isAfter(previous)) accessedAt else previous
            cache[url] = CacheDbEntry(url, lastModified, date, data, usage)
        }
        return Job().apply { complete() }
    }

    override suspend fun getCachedWebpage(url: String): CacheDbEntry? {
        return synchronized(cache) {
            cache[url]?.let { entry ->
                val now = TimeSource.now()
                entry.copy(lastAccessed = entry.lastAccessed?.takeIf { it.isAfter(now) } ?: now)
                    .also { cache[url] = it }
            }
        }
    }

    override suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime) {
        synchronized(cache) {
            cache[url]?.let { cache[url] = it.copy(lastChecked = dateTime) }
        }
    }

    override suspend fun evictUnusedCache(cutoff: ZonedDateTime): Long = synchronized(cache) {
        val before = cache.size
        cache.entries.removeIf { (_, entry) -> entry.lastAccessed?.isAfter(cutoff) == false }
        (before - cache.size).toLong()
    }
}
