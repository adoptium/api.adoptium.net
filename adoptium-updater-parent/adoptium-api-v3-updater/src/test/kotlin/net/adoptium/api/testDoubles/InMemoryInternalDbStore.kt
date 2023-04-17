package net.adoptium.api.testDoubles

import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import kotlinx.coroutines.Job
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import java.time.ZonedDateTime

@Priority(1)
@Alternative
@ApplicationScoped
class InMemoryInternalDbStore : InternalDbStore {
    private val cache: MutableMap<String, CacheDbEntry> = HashMap()
    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?): Job {
        cache[url] = CacheDbEntry(url, lastModified, date, data)
        return Job()
    }

    override suspend fun getCachedWebpage(url: String): CacheDbEntry? {
        return cache[url]
    }

    override suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime) {
        val cachedValue = cache[url]
        if (cachedValue != null) {
            cache[url] = CacheDbEntry(url, cachedValue.lastModified, dateTime, cachedValue.data)
        }
    }
}
