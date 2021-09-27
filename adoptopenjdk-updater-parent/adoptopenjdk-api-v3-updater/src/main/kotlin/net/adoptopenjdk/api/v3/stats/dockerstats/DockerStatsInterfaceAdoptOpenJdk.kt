package net.adoptopenjdk.api.v3.stats.dockerstats

import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.JvmImpl
import javax.inject.Inject

class DockerStatsInterfaceAdoptOpenJdk @Inject constructor(
    database: ApiPersistence,
    updaterHtmlClient: UpdaterHtmlClient,
) : DockerStats(database, updaterHtmlClient) {

    private val downloadStatsUrl = "https://hub.docker.com/v2/repositories/adoptopenjdk/"
    private val officialStatsUrl = "https://hub.docker.com/v2/repositories/library/adoptopenjdk/"

    override fun getDownloadStats(): List<DockerDownloadStatsDbEntry> {
        val now = TimeSource.now()

        return pullAllStats(downloadStatsUrl)
            .map {
                DockerDownloadStatsDbEntry(
                    now,
                    it.getJsonNumber("pull_count").longValue(),
                    it.getString("name"),
                    getOpenjdkVersionFromString(it.getString("name")),
                    if (it.getString("name").contains("openj9")) JvmImpl.openj9 else JvmImpl.hotspot // Will need to be updated with a new JVMImpl
                )
            }
    }

    override fun pullOfficalStats(): DockerDownloadStatsDbEntry {
        val result = getStatsForUrl(officialStatsUrl)
        val now = TimeSource.now()

        return DockerDownloadStatsDbEntry(now, result.getJsonNumber("pull_count").longValue(), "official", null, null)
    }
}
