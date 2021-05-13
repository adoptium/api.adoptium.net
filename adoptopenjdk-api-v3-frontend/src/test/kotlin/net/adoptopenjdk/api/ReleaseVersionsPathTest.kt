package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.stream.Stream

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class ReleaseVersionsPathTest : AssetsPathTest() {

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

    override fun <T> runFilterTest(filterParamName: String, values: Array<T>): Stream<DynamicTest> {
        return values
            .map { value ->
                DynamicTest.dynamicTest(value.toString()) {
                    RestAssured.given()
                        .`when`()
                        .get("/v3/info/release_versions?$filterParamName=$value")
                        .then()
                        .statusCode(200)
                }
            }
            .stream()
    }
}
