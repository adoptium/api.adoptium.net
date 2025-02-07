package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.routes.AssetsResource
import net.adoptium.api.v3.routes.ReleaseEndpoint
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.stream.Stream

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class AssetsResourceReleaseNamePathTest : FrontendTest() {

    @Inject
    lateinit var apiDataStore: APIDataStore

    @TestFactory
    fun filtersByReleaseNameCorrectly(): Stream<DynamicTest> {
        return Vendor.entries
            .flatMap { vendor ->
                apiDataStore
                    .getAdoptRepos()
                    .allReleases
                    .getReleases(releaseFilterFactory.createFilter(vendor = vendor), SortOrder.DESC, SortMethod.DEFAULT)
                    .take(3)
                    .flatMap { release ->
                        ReleaseType.entries
                            .map { "/v3/assets/release_name/$vendor/${release.release_name}" }
                            .map {
                                DynamicTest.dynamicTest(it) {
                                    RestAssured.given()
                                        .`when`()
                                        .get(it)
                                        .then()
                                        .statusCode(200)
                                        .and()
                                        .body(object : TypeSafeMatcher<String>() {
                                            override fun describeTo(description: Description?) {
                                                description!!.appendText("json")
                                            }

                                            override fun matchesSafely(p0: String?): Boolean {
                                                val returnedRelease = JsonMapper.mapper.readValue(p0, Release::class.java)
                                                return returnedRelease.id == release.id
                                            }
                                        })
                                }
                            }
                            .asSequence()
                    }
                    .asIterable()
            }
            .stream()
    }

    @Test
    fun `non-existent release name 404s`() {
        RestAssured.given()
            .`when`()
            .get("/v3/assets/release_name/adoptopenjdk/foo")
            .then()
            .statusCode(404)
    }

    @Test
    fun `for frontend requests x86 == x32`() {
        val releaseName = apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(releaseFilterFactory.createFilter(vendor = Vendor.getDefault()), BinaryFilter(arch = Architecture.x32), SortOrder.DESC, SortMethod.DEFAULT)
            .first()
            .release_name

        RestAssured.given()
            .`when`()
            .get("/v3/assets/release_name/${Vendor.getDefault()}/$releaseName?architecture=x86")
            .then()
            .statusCode(200)
            .and()
            .body(object : TypeSafeMatcher<String>() {
                override fun describeTo(description: Description?) {
                    description!!.appendText("json")
                }

                override fun matchesSafely(p0: String?): Boolean {
                    val returnedRelease = JsonMapper.mapper.readValue(p0, Release::class.java)
                    return returnedRelease.binaries.filter { it.architecture == Architecture.x32 }.isNotEmpty() &&
                        returnedRelease.binaries.filter { it.architecture != Architecture.x32 }.isEmpty()
                }
            })
    }

    @Test
    fun `release with different vendor 404s`() {
        val releaseName = apiDataStore
            .getAdoptRepos()
            .allReleases
            .getReleases(releaseFilterFactory.createFilter(vendor = Vendor.getDefault()), SortOrder.DESC, SortMethod.DEFAULT)
            .first()
            .release_name

        RestAssured.given()
            .`when`()
            .get("/v3/assets/release_name/openjdk/$releaseName")
            .then()
            .statusCode(404)
    }

    /*
    @Test
    fun `missing release_name 400s`() {
        assertThrows<BadRequestException> {
            AssetsResource(apiDataStore, ReleaseEndpoint(apiDataStore, releaseFilterFactory), releaseFilterFactory)
                .get(
                    Vendor.adoptopenjdk,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
        }
    }

    @Test
    fun `missing vendor 400s`() {
        assertThrows<BadRequestException> {
            AssetsResource(apiDataStore, ReleaseEndpoint(apiDataStore, releaseFilterFactory), releaseFilterFactory)
                .get(
                    null,
                    "foo",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
        }
    }

     */
}
