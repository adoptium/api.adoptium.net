package net.adoptium.api.v3.dataSources.persitence.mongo

import java.time.ZonedDateTime

data class UpdatedInfo(val time: ZonedDateTime, val checksum: String, val hashCode: Int) {
    override fun toString(): String {
        return "$time $checksum $hashCode"
    }
}
