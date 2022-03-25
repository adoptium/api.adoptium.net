package net.adoptium.marketplace.dataSources.persitence

import net.adoptium.marketplace.dataSources.ReleaseInfo
import net.adoptium.marketplace.dataSources.persitence.mongo.MongoClient
import net.adoptium.marketplace.dataSources.persitence.mongo.MongoVendorPersistence
import net.adoptium.marketplace.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.ReleaseUpdateInfo
import net.adoptium.marketplace.schema.Vendor
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

interface VendorPersistenceFactory {
    fun get(vendor: Vendor): VendorPersistence
}

@Singleton
class DefaultVendorPersistenceFactory @Inject constructor(
    var mongoClient: MongoClient
) : VendorPersistenceFactory {
    override fun get(vendor: Vendor): VendorPersistence {
        return MongoVendorPersistence(mongoClient, vendor)
    }
}

interface VendorPersistence {
    // Write a set of releases, duplicate entries will not be inserted
    // returns those that were successfully inserted
    suspend fun writeReleases(releases: ReleaseList): ReleaseUpdateInfo

    suspend fun getAllReleases(): ReleaseList

    suspend fun setReleaseInfo(releaseInfo: ReleaseInfo)
    suspend fun getReleaseInfo(): ReleaseInfo?

    suspend fun getUpdatedInfoIfUpdatedSince(since: Date): UpdatedInfo?

    suspend fun getReleaseVendorStatus(): List<ReleaseUpdateInfo>
}
