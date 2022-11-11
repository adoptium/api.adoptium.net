package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptium.api.v3.models.CLib
import org.hamcrest.core.IsNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class ReleaseNamesPathTest : AssetsPathTest() {

    @Test
    fun releaseNames() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/release_names")
            .then()
            .statusCode(200)
    }

    @Test
    fun `next page link is added`() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/release_names?page_size=10")
            .then()
            .statusCode(200)
            .header("Link", """<http://localhost:8080/v3/info/release_names?page=1&page_size=10>; rel="next"""")
    }

    @Test
    fun `next page link is not added when there is no next page`() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/release_names?page_size=100&page=1&c_lib=${CLib.musl}")
            .then()
            .statusCode(200)
            .header("Link", IsNull<String>())

    }

    @Test
    fun releaseNamesPageSize() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/release_names?page_size=50")
            .then()
            .statusCode(200)
    }

    @Test
    fun releaseNamesSortOrder() {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_names?sort_order=DESC")
            .then()
            .statusCode(200)
    }

    @ParameterizedTest
    @ValueSource(strings = ["[11,15]", "(11,15)", "(11,15]", "[11,15)"])
    fun releaseNamesVersionRanges(version: String) {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_names?version=$version")
            .then()
            .statusCode(200)
    }

    @ParameterizedTest
    @ValueSource(strings = ["[11,15", "(11,15", "11,15]", "11,15)", "11,15", "11,", ",15", "[11", "15)"])
    fun releaseNamesInvalidVersionRanges(version: String) {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_names?version=$version")
            .then()
            .statusCode(400)
    }

    @Test
    fun releaseVersions() {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_versions")
            .then()
            .statusCode(200)
    }

    @Test
    fun releaseVersionsPageSize() {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_versions?page_size=50")
            .then()
            .statusCode(200)
    }

    @Test
    fun releaseVersionsSortOrder() {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_versions?sort_order=ASC")
            .then()
            .statusCode(200)
    }

    override fun <T> runFilterTest(filterParamName: String, values: Array<T>, customiseQuery: (T, String) -> String): Stream<DynamicTest> {
        return values
            .map { value ->
                DynamicTest.dynamicTest(value.toString()) {
                    RestAssured.given()
                        .`when`()
                        .get(customiseQuery(value, "/v3/info/release_names?$filterParamName=$value"))
                        .then()
                        .statusCode(200)
                }
            }
            .stream()
    }

    @ParameterizedTest
    @ValueSource(strings = ["[11,15]", "(11,15)", "(11,15]", "[11,15)"])
    fun releaseVersionsVersionRanges(version: String) {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_versions?version=$version")
            .then()
            .statusCode(200)
    }

    @ParameterizedTest
    @ValueSource(strings = ["[11,15", "(11,15", "11,15]", "11,15)", "11,15", "11,", ",15", "[11", "15)"])
    fun releaseVersionsInvalidVersionRanges(version: String) {

        RestAssured.given()
            .`when`()
            .get("/v3/info/release_versions?version=$version")
            .then()
            .statusCode(400)
    }
}
