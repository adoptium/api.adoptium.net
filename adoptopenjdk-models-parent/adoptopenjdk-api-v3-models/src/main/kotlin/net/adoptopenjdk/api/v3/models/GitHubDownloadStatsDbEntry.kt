package net.adoptopenjdk.api.v3.models

import java.time.ZonedDateTime

class GitHubDownloadStatsDbEntry(
    date: ZonedDateTime,
    val downloads: Long,
    val jvmImplDownloads: Map<JvmImpl, Long>?,
    val feature_version: Int
) : DbStatsEntry<Int>(date) {
    override fun getMetric(): Long = downloads
    override fun getId(): Int = feature_version
}
