package net.adoptopenjdk.api.v3.stats.dockerstats

import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.DockerDownloadStatsDbEntry
import javax.inject.Inject

class DockerStatsInterfaceAdoptium @Inject constructor(
    database: ApiPersistence,
    updaterHtmlClient: UpdaterHtmlClient
) : DockerStats(database, updaterHtmlClient) {

    private val officialStatsUrl = "https://hub.docker.com/v2/repositories/library/eclipse-temurin/"

    override fun getDownloadStats(): List<DockerDownloadStatsDbEntry> {
        return emptyList()
    }

    override fun pullOfficalStats(): DockerDownloadStatsDbEntry {
        val result = getStatsForUrl(officialStatsUrl)
        val now = TimeSource.now()

        return DockerDownloadStatsDbEntry(now, result.getJsonNumber("pull_count").longValue(), "eclipse-temurin", null, null)
    }
}
