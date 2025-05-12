package net.adoptium.api.v3.stats.dockerstats

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.config.Ecosystem
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.DockerDownloadStatsDbEntry
import org.slf4j.LoggerFactory
import jakarta.json.JsonObject
import net.adoptium.api.v3.stats.StatsInterface

@ApplicationScoped
open class DockerStatsInterfaceFactory @Inject constructor(
    private var database: ApiPersistence,
    private val updaterHtmlClient: UpdaterHtmlClient
) {
    var cached: DockerStatsInterface? = null

    @Produces
    @ApplicationScoped
    fun get(): DockerStatsInterface {
        if (cached == null) {
            cached = when (Ecosystem.CURRENT) {
                Ecosystem.adoptopenjdk -> DockerStatsInterfaceAdoptOpenJdk(database, updaterHtmlClient)
                Ecosystem.adoptium -> DockerStatsInterfaceAdoptium(database, updaterHtmlClient)
            }
        }

        return cached!!
    }

    open fun getDockerStatsInterface(): StatsInterface {
        TODO("Not yet implemented")
    }
}

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
    abstract fun pullOfficialStats(): DockerDownloadStatsDbEntry

    override suspend fun updateDb() {
        try {
            val stats = mutableListOf<DockerDownloadStatsDbEntry>()

            stats.addAll(getDownloadStats())
            stats.add(pullOfficialStats())

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
