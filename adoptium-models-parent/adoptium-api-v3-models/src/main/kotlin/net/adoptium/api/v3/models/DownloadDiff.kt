package net.adoptium.api.v3.models

import java.time.ZonedDateTime

class DownloadDiff(
    val date: ZonedDateTime,
    val total: Long,
    val daily: Long
)
