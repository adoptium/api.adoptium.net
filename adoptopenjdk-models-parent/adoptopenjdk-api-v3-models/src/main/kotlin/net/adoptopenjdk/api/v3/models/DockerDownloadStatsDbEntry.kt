package net.adoptopenjdk.api.v3.models

import java.time.ZonedDateTime

class DockerDownloadStatsDbEntry(
    date: ZonedDateTime,
    val pulls: Long,
    val repo: String,
    val feature_version: Int?,
    val jvm_impl: JvmImpl?
) : DbStatsEntry<String>(date) {
    override fun getMetric(): Long = pulls
    override fun getId(): String = repo
}
