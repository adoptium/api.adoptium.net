package net.adoptium.api.v3

import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.models.Vendor
import java.time.Duration
import java.time.ZonedDateTime

enum class ReleaseFilterType {
    RELEASES_ONLY,
    SNAPSHOTS_ONLY,
    ALL
}

class ReleaseIncludeFilter(
    private val now: ZonedDateTime,
    private val filterType: ReleaseFilterType,
    private val includeAll: Boolean = false,
    private val excludedVendors: Set<Vendor> = VENDORS_EXCLUDED_FROM_FULL_UPDATE
) {
    companion object {
        val VENDORS_EXCLUDED_FROM_FULL_UPDATE = setOf(Vendor.adoptopenjdk)

        val INCLUDE_ALL = ReleaseIncludeFilter(TimeSource.now(), ReleaseFilterType.ALL, true)
    }

    fun filterVendor(vendor: Vendor): Boolean {
        return if (includeAll || APIConfig.UPDATE_ADOPTOPENJDK) {
            true // include all vendors
        } else {
            !excludedVendors.contains(vendor)
        }
    }

    fun filter(vendor: Vendor, startTime: String, isPrerelease: Boolean): Boolean {
        return filter(vendor, ReleaseMapper.parseDate(startTime), isPrerelease)
    }

    fun filter(vendor: Vendor, startTime: ZonedDateTime, isPrerelease: Boolean): Boolean {
        if (includeAll || APIConfig.UPDATE_ADOPTOPENJDK) {
            return true // include all vendors
        } else {
            if (excludedVendors.contains(vendor)) {
                return false
            }

            var include = true

            if (filterType == ReleaseFilterType.RELEASES_ONLY) {
                return !isPrerelease
            } else if (filterType == ReleaseFilterType.SNAPSHOTS_ONLY) {
                include = isPrerelease
            }

            val isOldPrerelease = isPrerelease && Duration.between(startTime, now).toDays() > APIConfig.UPDATE_DAY_CUTOFF

            return include && !isOldPrerelease
            // exclude AdoptOpenjdk
            // Don't Update releases more than a year old
        }
    }
}
