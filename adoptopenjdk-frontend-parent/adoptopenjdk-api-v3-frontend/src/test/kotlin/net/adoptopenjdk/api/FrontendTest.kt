package net.adoptopenjdk.api

import net.adoptopenjdk.api.testDoubles.InMemoryApiPersistence
import net.adoptopenjdk.api.testDoubles.InMemoryInternalDbStore
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.SortMethod
import net.adoptopenjdk.api.v3.dataSources.SortOrder
import net.adoptopenjdk.api.v3.filters.BinaryFilter
import net.adoptopenjdk.api.v3.filters.ReleaseFilter
import net.adoptopenjdk.api.v3.models.Binary
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.Vendor
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
        val release = BaseTest.adoptRepos
            .getFilteredReleases(
                ReleaseFilter(featureVersion = 8, vendor = Vendor.getDefault()),
                BinaryFilter(),
                SortOrder.ASC,
                SortMethod.DEFAULT
            ).first()

        val binary = release.binaries.first()
        return Pair(release, binary)
    }
}
