package net.adoptium.marketplace.dataSources.persitence.mongo

import java.util.*

data class UpdatedInfo(val time: Date) {
    override fun toString(): String {
        return "$time"
    }
}
