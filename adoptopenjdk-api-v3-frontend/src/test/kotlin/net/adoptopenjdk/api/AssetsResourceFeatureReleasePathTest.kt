package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import junit.framework.Assert.fail
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.dataSources.SortMethod
import net.adoptopenjdk.api.v3.dataSources.SortOrder
import net.adoptopenjdk.api.v3.dataSources.models.Releases
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import org.hamcrest.Description
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import java.util.stream.Stream
import kotlin.test.assertTrue

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class AssetsResourceFeatureReleasePathTest : AssetsPathTest() {

    @TestFactory
    fun noFilter(): Stream<DynamicTest> {
        return AdoptReposTestDataGenerator.TEST_VERSIONS
            .flatMap { version ->
                ReleaseType.values()
                    .map { "/v3/assets/feature_releases/$version/$it" }
                    .map {
                        DynamicTest.dynamicTest(it) {
                            RestAssured.given()
                                .`when`()
                                .get(it)
                                .then()
                                .body("binaries.size()", Matchers.greaterThan(0))
                                .statusCode(200)
                        }
                    }
            }
            .stream()
    }

    @TestFactory
    fun `no Vendor Defaults To AdoptopenJDK`(): Stream<DynamicTest> {
        return AdoptReposTestDataGenerator.TEST_VERSIONS
            .flatMap { version ->
                ReleaseType.values()
                    .map { "/v3/assets/feature_releases/$version/$it?PAGE_SIZE=100" }
                    .map { request ->
                        DynamicTest.dynamicTest(request) {
                            RestAssured.given()
                                .`when`()
                                .get(request)
                                .then()
                                .body(object : TypeSafeMatcher<String>() {

                                    override fun describeTo(description: Description?) {
                                        description!!.appendText("json")
                                    }

                                    override fun matchesSafely(p0: String?): Boolean {
                                        val releases = JsonMapper.mapper.readValue(p0, Array<Release>::class.java)
                                        return releases
                                            .none { it.vendor != Vendor.adoptopenjdk }
                                    }
                                })
                                .statusCode(200)
                        }
                    }
            }
            .stream()
    }

    @Test
    fun badReleaseType() {
        RestAssured.given()
            .`when`()
            .get("${getPath()}/8/foo")
            .then()
            .statusCode(404)
    }

    @Test
    fun badVersion() {
        RestAssured.given()
            .`when`()
            .get("2/${getPath()}")
            .then()
            .statusCode(404)
    }

    @Test
    fun sortOrderASCIsHonoured() {
        getReleases(SortOrder.ASC)
            .fold(
                null,
                { previous: Release?, next: Release ->
                    if (previous != null) {
                        if (Releases.VERSION_COMPARATOR.compare(previous.version_data, next.version_data) > 0) {
                            fail("${previous.version_data} is before ${next.version_data}")
                        }
                    }
                    next
                }
            )
    }

    @Test
    fun sortOrderDESIsHonoured() {
        getReleases(SortOrder.DESC)
            .fold(
                null,
                { previous: Release?, next: Release ->
                    if (previous != null) {
                        if (Releases.VERSION_COMPARATOR.compare(previous.version_data, next.version_data) < 0) {
                            fail("${previous.version_data} is before ${next.version_data}")
                        }
                    }
                    next
                }
            )
    }

    override fun <T> runFilterTest(filterParamName: String, values: Array<T>): Stream<DynamicTest> {
        return ReleaseType.values()
            .flatMap { releaseType ->
                // test the ltses and 1 non-lts
                listOf(8, 11, 12)
                    .flatMap { version ->
                        createTest(
                            values, "${getPath()}/$version/$releaseType", filterParamName,
                            { element ->
                                getExclusions(version, element)
                            }
                        )
                    }
            }
            .stream()
    }

    private fun <T> getExclusions(version: Int, element: T): Boolean {
        return version == 11 && element == OperatingSystem.solaris ||
            version == 12 && element == OperatingSystem.solaris ||
            version == 8 && element == Architecture.arm ||
            version != 8 && element == Architecture.sparcv9 ||
            version == 8 && element == ImageType.testimage ||
            version == 11 && element == ImageType.testimage ||
            version == 12 && element == ImageType.testimage ||

            element == Architecture.riscv64 || // Temporary until riscv ga

            element == ImageType.debugimage ||
            element == ImageType.staticlibs ||
            element == OperatingSystem.`alpine-linux`
    }

    companion object {
        fun getPath() = "/v3/assets/feature_releases"
        fun getReleases(sortOrder: SortOrder): List<Release> {
            val body = RestAssured.given()
                .`when`()
                .get("${getPath()}/8/ga?sort_order=${sortOrder.name}")
                .body

            return parseReleases(body.asString())
        }

        fun getReleasesWithSortMethod(sortOrder: SortOrder, sortMethod: SortMethod): List<Release> {
            val body = RestAssured.given()
                .`when`()
                .get("${getPath()}/8/ga?sort_order=${sortOrder.name}&sort_method=${sortMethod.name}")
                .body

            return parseReleases(body.asString())
        }

        fun parseReleases(json: String?): List<Release> =
            JsonMapper.mapper.readValue(json, JsonMapper.mapper.getTypeFactory().constructCollectionType(MutableList::class.java, Release::class.java))
    }

    @Test
    fun pagination() {
        RestAssured.given()
            .`when`()
            .get("${getPath()}/8/ga?page_size=1&page=1")
            .then()
            .statusCode(200)
    }

    @Test
    fun pageSizeIsWorking() {
        val body = RestAssured.given()
            .`when`()
            .get("${getPath()}/11/ea?page_size=5")
            .body

        val releases = parseReleases(body.asString())

        assertTrue { releases.size == 5 }
    }

    @TestFactory
    fun beforeFilter(): Stream<DynamicTest> {
        return listOf(
            Pair("2099-01-01", 200),
            Pair("2099-01-01T10:15:30", 200),
            Pair("20990101", 200),
            Pair("2099-12-03T10:15:30Z", 200),
            Pair("2099-12-03+01:00", 200),

            Pair("2000-01-01", 404),
            Pair("2000-01-01T10:15:30", 404),
            Pair("20000101", 404),
            Pair("2000-12-03T10:15:30Z", 404),
            Pair("2000-12-03+01:00", 404),

            Pair("foo", 404)
        )
            .map {
                DynamicTest.dynamicTest(it.first) {
                    RestAssured.given()
                        .`when`()
                        .get("${getPath()}/11/ea?before=${it.first}")
                        .then()
                        .statusCode(it.second)
                }
            }
            .stream()
    }
}
