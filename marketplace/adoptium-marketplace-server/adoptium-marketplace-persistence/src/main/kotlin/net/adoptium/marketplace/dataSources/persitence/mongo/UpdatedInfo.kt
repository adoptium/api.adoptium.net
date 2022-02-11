package net.adoptium.marketplace.dataSources.persitence.mongo

import java.time.ZonedDateTime

data class UpdatedInfo(val time: ZonedDateTime) {
    override fun toString(): String {
        return "$time"
    }
}
