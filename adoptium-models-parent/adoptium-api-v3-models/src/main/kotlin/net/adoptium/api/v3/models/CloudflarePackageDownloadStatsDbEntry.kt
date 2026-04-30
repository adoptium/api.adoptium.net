package net.adoptium.api.v3.models

import java.time.ZonedDateTime

class CloudflarePackageDownloadStatsDbEntry(
    date: ZonedDateTime,
    val downloads: Long,

    val feature_version: Int
) : DbStatsEntry<Int>(date) {
    override fun getMetric(): Long = downloads
    override fun getId(): Int = feature_version
}
