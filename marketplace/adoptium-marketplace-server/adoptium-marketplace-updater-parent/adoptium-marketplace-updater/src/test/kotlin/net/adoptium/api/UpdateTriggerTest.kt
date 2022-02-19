package net.adoptium.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import io.mockk.MockKAnnotations
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import kotlinx.coroutines.runBlocking
import net.adoptium.marketplace.client.TestServer
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.dataSources.APIDataStoreImpl
import net.adoptium.marketplace.dataSources.VendorReleases
import net.adoptium.marketplace.dataSources.VendorReleasesFactoryImpl
import net.adoptium.marketplace.dataSources.persitence.DefaultVendorPersistenceFactory
import net.adoptium.marketplace.dataSources.persitence.mongo.MongoClient
import net.adoptium.marketplace.dataSources.persitence.mongo.MongoTest
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.server.updater.VendorInfo
import net.adoptium.marketplace.server.updater.VendorList
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAlternatives
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.id.jackson.IdJacksonModule
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.util.KMongoConfiguration
import javax.annotation.Priority
import javax.enterprise.inject.Alternative
import javax.inject.Singleton
import javax.ws.rs.core.Response

@Priority(1)
@Alternative
@Singleton
class MockVendorList : VendorList {
    override fun getVendorInfo(): Map<Vendor, VendorInfo> {
        return mapOf(
            Vendor.adoptium to VendorInfo(Vendor.adoptium, "http://localhost:8090/workingRepository", "../../../exampleRepositories/keys/public.pem")
        )
    }
}

@Priority(1)
@Alternative
@Singleton
class FongoClient : MongoClient {

    private val settingsBuilder: MongoClientSettings.Builder = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(System.getProperty("MONGODB_TEST_CONNECTION_STRING")))

    private val db = KMongo.createClient(settingsBuilder.build()).coroutine.getDatabase("test-api")

    init {
        KMongoConfiguration.registerBsonModule(IdJacksonModule())
        KMongoConfiguration.registerBsonModule(Jdk8Module())
        KMongoConfiguration.registerBsonModule(JavaTimeModule())
        KMongoConfiguration.bsonMapper.disable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
        KMongoConfiguration.bsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        KMongoConfiguration.bsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    override fun getDatabase(): CoroutineDatabase {
        return db
    }
}

@QuarkusTest
@EnableAlternatives
@EnableAutoWeld
@ExtendWith(
    value = [
        TestServer::class,
        MongoTest::class
    ]
)
@AddPackages(
    value = [
        APIDataStore::class,
        VendorList::class
    ]
)
class UpdateTriggerTest {

    @BeforeEach
    fun setUp() = MockKAnnotations.init(this, relaxUnitFun = true)

    @Test
    fun `db is updated`() {
        runBlocking {
            VendorReleases.UPDATE_COOLOFF_IN_SECONDS = 0

            val apiDataStore = APIDataStoreImpl(VendorReleasesFactoryImpl(DefaultVendorPersistenceFactory(FongoClient())))

            var releases = apiDataStore.getReleases(Vendor.adoptium).getAllReleases()
            Assertions.assertTrue(releases.releases.size == 0)

            getRequest()
                .`when`()
                .get("/updateForVendor/adoptium")
                .then()
                .statusCode(Response.Status.OK.statusCode)

            releases = apiDataStore.getReleases(Vendor.adoptium).getAllReleases()
            Assertions.assertTrue(releases.releases.size > 0)
        }
    }

    @Test
    fun `user is restricted to their own vendor`() {
        RestAssured.given()
            .auth()
            .preemptive()
            .basic("otherTestUpdaterUser", "a-test-token")
            .`when`()
            .get("/updateForVendor/adoptium")
            .then()
            .statusCode(Response.Status.NOT_FOUND.statusCode)
    }

    @Test
    fun `bad password is rejected`() {
        RestAssured.given()
            .auth()
            .preemptive()
            .basic("testUpdaterUser", "badPasswd")
            .`when`()
            .get("/updateForVendor/adoptium")
            .then()
            .statusCode(Response.Status.UNAUTHORIZED.statusCode)
    }

    private fun getRequest() = RestAssured.given()
        .auth()
        .preemptive()
        .basic("testUpdaterUser", "a-test-token")
        .`when`()
}
