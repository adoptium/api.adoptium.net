package net.adoptopenjdk.api.v3.stats.dockerstats

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.config.Ecosystem
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UpdaterJsonMapper
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.DockerDownloadStatsDbEntry
import org.slf4j.LoggerFactory
import javax.enterprise.inject.Produces
import javax.inject.Inject
import javax.inject.Singleton
import javax.json.JsonObject

@Singleton
class DockerStatsInterfaceFactory @Inject constructor(
    private var database: ApiPersistence,
    private val updaterHtmlClient: UpdaterHtmlClient
) {
    var cached: DockerStatsInterface? = null

    @Produces
    @Singleton
    fun get(): DockerStatsInterface {
        if (cached == null) {
            cached = when (Ecosystem.CURRENT) {
                Ecosystem.adoptopenjdk -> DockerStatsInterfaceAdoptOpenJdk(database, updaterHtmlClient)
                Ecosystem.adoptium -> DockerStatsInterfaceAdoptium(database, updaterHtmlClient)
            }
        }

        return cached!!
    }
}

@Singleton
interface DockerStatsInterface {
    suspend fun updateDb()
}

abstract class DockerStats @Inject constructor(
    private var database: ApiPersistence,
    private val updaterHtmlClient: UpdaterHtmlClient
) : DockerStatsInterface {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        fun getOpenjdkVersionFromString(name: String): Int? {
            return "openjdk(?<featureNum>[0-9]+)".toRegex().find(name)?.groups?.get("featureNum")?.value?.toInt()
        }
    }

    abstract fun getDownloadStats(): List<DockerDownloadStatsDbEntry>
    abstract fun pullOfficalStats(): DockerDownloadStatsDbEntry

    override suspend fun updateDb() {
        try {
            val stats = mutableListOf<DockerDownloadStatsDbEntry>()

            stats.addAll(getDownloadStats())
            stats.add(pullOfficalStats())

            database.addDockerDownloadStatsEntries(stats)
        } catch (e: Exception) {
            LOGGER.error("Failed to fetch docker download stats", e)
            throw e
        }
    }

    protected fun pullAllStats(downloadStatsUrl: String): ArrayList<JsonObject> {
        var next: String? = downloadStatsUrl

        val results = ArrayList<JsonObject>()
        while (next != null) {
            val stats = getStatsForUrl(next)
            results.addAll(stats.getJsonArray("results").map { it as JsonObject })
            next = stats.getString("next", null)
        }
        return results
    }

    protected fun getStatsForUrl(url: String): JsonObject {
        return runBlocking {
            val stats = updaterHtmlClient.get(url)
            if (stats == null) {
                throw Exception("Stats not returned")
            }

            try {
                val json = UpdaterJsonMapper.mapper.readValue(stats, JsonObject::class.java)
                if (json == null) {
                    throw Exception("Failed to parse stats")
                }
                return@runBlocking json
            } catch (e: Exception) {
                throw Exception("Failed to parse stats", e)
            }
        }
    }
}
