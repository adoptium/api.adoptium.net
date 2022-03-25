package net.adoptium.marketplace.dataSources

import net.adoptium.marketplace.dataSources.persitence.VendorPersistence
import net.adoptium.marketplace.dataSources.persitence.VendorPersistenceFactory
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.ReleaseUpdateInfo
import net.adoptium.marketplace.schema.Vendor
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

interface VendorReleasesFactory {
    fun get(vendor: Vendor): VendorReleases
}

@Singleton
class VendorReleasesFactoryImpl @Inject constructor(
    private val vendorPersistenceFactory: VendorPersistenceFactory
) : VendorReleasesFactory {
    override fun get(vendor: Vendor): VendorReleases {
        return VendorReleases(vendorPersistenceFactory.get(vendor))
    }
}

class VendorReleases constructor(
    private val vendorPersistence: VendorPersistence
) {
    private var releaseList: ReleaseList? = null
    private var lastUpdated: Date = Date.from(ZonedDateTime.now().minusDays(100).toInstant())
    private var lastCheck: ZonedDateTime? = null
    private var releaseInfo: ReleaseInfo? = null

    companion object {
        var UPDATE_COOLOFF_IN_SECONDS = 60
    }

    suspend fun getAllReleases(): ReleaseList {
        releaseList = if (releaseList == null) {
            update()
        } else {
            if (Duration.between(lastCheck, TimeSource.now()).seconds > UPDATE_COOLOFF_IN_SECONDS) {
                lastCheck = TimeSource.now()
                val updated = vendorPersistence.getUpdatedInfoIfUpdatedSince(lastUpdated)
                if (updated != null) {
                    update()
                } else {
                    releaseList
                }
            } else {
                releaseList
            }
        }

        return releaseList!!
    }

    private suspend fun update(): ReleaseList {
        lastCheck = TimeSource.now()
        val updatedTime = vendorPersistence.getUpdatedInfoIfUpdatedSince(lastUpdated)
        lastUpdated = updatedTime?.time ?: lastUpdated
        releaseList = vendorPersistence.getAllReleases()
        releaseInfo = formReleaseInfo(releaseList!!)
        return releaseList!!
    }

    fun formReleaseInfo(releases: ReleaseList): ReleaseInfo {

        val availableReleases = releases
            .releases
            .map { it.openjdkVersionData.major }
            .distinct()
            .sorted()
            .toList()
            .toTypedArray()

        val availableLtsReleases: Array<Int> = releases
            .releases
            .asSequence()
            .filter { it.openjdkVersionData.isLts }
            .map { it.openjdkVersionData.major }
            .distinct()
            .sorted()
            .toList()
            .toTypedArray()

        val mostRecentLts = availableLtsReleases.lastOrNull() ?: 0

        val mostRecentFeatureVersion: Int = releases
            .releases
            .map { it.openjdkVersionData.major }
            .distinct()
            .sorted()
            .lastOrNull() ?: 0

        return ReleaseInfo(
            availableReleases,
            availableLtsReleases,
            mostRecentLts,
            mostRecentFeatureVersion
        )
    }

    suspend fun getReleaseVendorStatus(): List<ReleaseUpdateInfo> {
        return vendorPersistence.getReleaseVendorStatus()
    }

    suspend fun writeReleases(releaseList: ReleaseList): ReleaseUpdateInfo {
        val newReleases = vendorPersistence.writeReleases(releaseList)
        update()
        return newReleases
    }

    suspend fun getReleaseInfo(): ReleaseInfo {
        if (releaseInfo == null) {
            update()
        }
        return releaseInfo!!
    }
}
