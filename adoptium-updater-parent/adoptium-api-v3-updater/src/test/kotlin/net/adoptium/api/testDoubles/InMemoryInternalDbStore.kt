package net.adoptium.api.testDoubles

import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import kotlinx.coroutines.Job
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

@Priority(1)
@Alternative
@ApplicationScoped
class InMemoryInternalDbStore : InternalDbStore {
    private val cache = ConcurrentHashMap<String, CacheDbEntry>()
    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime?): Job {
        cache.compute(url) { _, entry ->
            val lastAccessed = entry?.lastAccessed
            val usage = if (accessedAt == null) lastAccessed ?: date
                else if (lastAccessed == null || lastAccessed.isBefore(accessedAt)) accessedAt else lastAccessed
            CacheDbEntry(url, lastModified, date, data, usage)
        }
        return Job().apply { complete() }
    }

    override suspend fun getCachedWebpage(url: String): CacheDbEntry? {
        return cache.computeIfPresent(url) { _, entry ->
            val now = TimeSource.now()
            val lastAccessed = entry.lastAccessed
            if (lastAccessed == null || lastAccessed.isBefore(now)) entry.copy(lastAccessed = now) else entry
        }
    }

    override suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime) {
        cache.computeIfPresent(url) { _, entry -> entry.copy(lastChecked = dateTime) }
    }

    override suspend fun evictUnusedCache(now: ZonedDateTime): Long {
        val cutoff = now.minusDays(InternalDbStore.CACHE_RETENTION_DAYS)
        val before = cache.size
        cache.entries.removeIf { (_, entry) ->
            entry.lastAccessed?.let { !it.isAfter(cutoff) } == true
        }
        return (before - cache.size).toLong()
    }
}
