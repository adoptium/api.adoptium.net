package net.adoptium.marketplace.dataSources.persitence.mongo

import kotlinx.coroutines.runBlocking
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.dataSources.ModelComparators
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.RepoGenerator
import net.adoptium.marketplace.schema.Vendor
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@EnableAutoWeld
class PersistenceTest : MongoTest() {

    companion object {
        val releaseList = RepoGenerator.generate()!!
    }

    @BeforeEach
    fun cleanDb(mongoClient: MongoClient) {
        runBlocking {
            mongoClient.database.dropCollection("adoptium_" + MongoVendorPersistence.RELEASE_DB)
            mongoClient.database.dropCollection("adoptium_" + MongoVendorPersistence.RELEASE_INFO_DB)
            mongoClient.database.dropCollection("adoptium_" + MongoVendorPersistence.UPDATE_TIME_DB)
        }
    }

    @Test
    fun `can write then read back data`(apiDataStore: APIDataStore) {
        runBlocking {
            val added = apiDataStore.getReleases(Vendor.adoptium).writeReleases(releaseList)
            val releases = apiDataStore.getReleases(Vendor.adoptium).getAllReleases()

            Assertions.assertTrue(ModelComparators.RELEASE_LIST.compare(added, releases) == 0)
            Assertions.assertTrue(ModelComparators.RELEASE_LIST.compare(releaseList, releases) == 0)
        }
    }

    @Test
    fun `writing data twice does not update`(apiDataStore: APIDataStore) {
        runBlocking {
            apiDataStore.getReleases(Vendor.adoptium).writeReleases(releaseList)
            val added2 = apiDataStore.getReleases(Vendor.adoptium).writeReleases(releaseList)
            Assertions.assertEquals(0, added2.releases.size)
        }
    }

    @Test
    fun `adding a new release works`(apiDataStore: APIDataStore) {
        runBlocking {

            val added = apiDataStore.getReleases(Vendor.adoptium).writeReleases(releaseList)
            val releases = apiDataStore.getReleases(Vendor.adoptium).getAllReleases()

            Assertions.assertTrue(ModelComparators.RELEASE_LIST.compare(added, releases) == 0)
            Assertions.assertTrue(ModelComparators.RELEASE_LIST.compare(releaseList, releases) == 0)

            val biggerList = ReleaseList(releaseList.releases.plus(RepoGenerator.generate("foo").releases))
            val added2 = apiDataStore.getReleases(Vendor.adoptium).writeReleases(biggerList)
            val releases2 = apiDataStore.getReleases(Vendor.adoptium).getAllReleases()
            Assertions.assertTrue(added2.releases.size == 1)
            Assertions.assertTrue(ModelComparators.RELEASE_LIST.compare(biggerList, releases2) == 0)

        }
    }
}
