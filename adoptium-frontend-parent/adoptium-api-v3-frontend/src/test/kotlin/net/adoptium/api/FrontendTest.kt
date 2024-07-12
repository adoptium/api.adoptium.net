package net.adoptium.api

import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
import net.adoptium.api.testDoubles.UpdatableVersionSupplierStub
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.filters.ReleaseFilterFactory
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.Vendor
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAutoWeld
import java.util.function.Predicate

@EnableAutoWeld
@AddPackages(
    value = [
        InMemoryApiPersistence::class,
        InMemoryInternalDbStore::class,
        APIDataStore::class
    ]
)
open class FrontendTest {

    var releaseFilterFactory: ReleaseFilterFactory = ReleaseFilterFactory(UpdatableVersionSupplierStub())

    protected fun getReleases(): Sequence<Release> {
        return BaseTest.adoptRepos
            .getFilteredReleases(
                releaseFilterFactory.createFilter(featureVersion = 8, vendor = Vendor.getDefault()),
                BinaryFilter(),
                SortOrder.ASC,
                SortMethod.DEFAULT
            )
    }

    protected fun getRandomBinary(): Pair<Release, Binary> {
        return getRandomBinary(
            releaseFilterFactory.createFilter(featureVersion = 8, vendor = Vendor.getDefault()),
            BinaryFilter(imageType = ImageType.jdk)
        )
    }

    protected fun getRandomBinary(releaseFilter: Predicate<Release>, binaryFilter: BinaryFilter): Pair<Release, Binary> {
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
