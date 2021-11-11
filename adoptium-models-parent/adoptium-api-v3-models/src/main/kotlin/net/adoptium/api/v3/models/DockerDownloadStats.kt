package net.adoptium.api.v3.models

class DockerDownloadStats(
    val total_downloads: Long,
    val downloads: Map<String, Long>,
    val total_daily_downloads: Long,
    val daily_downloads: Map<String, Long>
)
