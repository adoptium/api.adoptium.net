package net.adoptium.api

import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.filters.ReleaseFilter
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.Vendor
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAutoWeld

@EnableAutoWeld
@AddPackages(
    value = [
        InMemoryApiPersistence::class,
        InMemoryInternalDbStore::class,
        APIDataStore::class
    ]
)
open class FrontendTest {

    protected fun getReleases(): Sequence<Release> {
        return BaseTest.adoptRepos
            .getFilteredReleases(
                ReleaseFilter(featureVersion = 8, vendor = Vendor.getDefault()),
                BinaryFilter(),
                SortOrder.ASC,
                SortMethod.DEFAULT
            )
    }

    protected fun getRandomBinary(): Pair<Release, Binary> {
        return getRandomBinary(
            ReleaseFilter(featureVersion = 8, vendor = Vendor.getDefault()),
            BinaryFilter(imageType = ImageType.jdk)
        )
    }

    protected fun getRandomBinary(releaseFilter: ReleaseFilter, binaryFilter: BinaryFilter): Pair<Release, Binary> {
        val release = BaseTest.adoptRepos
            .getFilteredReleases(
                releaseFilter,
                binaryFilter,
                SortOrder.ASC,
                SortMethod.DEFAULT
            ).first()

        val binary = release.binaries.first()
        return Pair(release, binary)
    }
}
