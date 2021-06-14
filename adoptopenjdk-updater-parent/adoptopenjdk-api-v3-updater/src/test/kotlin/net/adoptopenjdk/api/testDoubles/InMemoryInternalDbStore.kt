package net.adoptopenjdk.api.testDoubles

import kotlinx.coroutines.Job
import net.adoptopenjdk.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptopenjdk.api.v3.dataSources.mongo.InternalDbStore
import java.time.ZonedDateTime
import javax.annotation.Priority
import javax.enterprise.inject.Alternative
import javax.inject.Singleton

@Priority(1)
@Alternative
@Singleton
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
