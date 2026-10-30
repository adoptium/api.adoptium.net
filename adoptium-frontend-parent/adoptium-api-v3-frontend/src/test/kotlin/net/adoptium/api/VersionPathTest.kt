package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.parser.VersionParser
import org.hamcrest.Description
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasKey
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

@QuarkusTest
class VersionPathTest {

    class VersionDataMatcher(val expected: VersionData) : TypeSafeMatcher<String>() {

        override fun describeTo(description: Description?) {
            description!!.appendText("json")
        }

        override fun matchesSafely(p0: String?): Boolean {
            val expectedJson = JsonMapper.mapper.writeValueAsString(expected)
            JSONAssert.assertEquals(expectedJson, p0, false)
            return true
        }
    }

    @Test
    fun parsesVersion() {

        val parsed = VersionParser.parse("jdk-11.0.5+10")

        RestAssured.given()
            .`when`()
            .get("/v3/version/jdk-11.0.5+10")
            .then()
            .statusCode(200)
            .body(VersionDataMatcher(parsed))
    }

    @Test
    fun hidesDerivedVersionNumbers() {
        listOf("11.0.5+10", "25.0.5.0.2+1").forEach { version ->
            RestAssured.given()
                .`when`()
                .get("/v3/version/$version")
                .then()
                .statusCode(200)
                .body(VersionDataMatcher(VersionParser.parse(version)))
                .body("$", not(hasKey("version_numbers")))
        }
    }

    @Test
    fun preservesAdditionalVersionNumbers() {
        RestAssured.given()
            .`when`()
            .get("/v3/version/25.0.5.0.2+1")
            .then()
            .statusCode(200)
            .body("additional_version_numbers", equalTo(listOf(2)))
    }

    @Test
    fun hidesDerivedVersionNumbersFromOpenApi() {
        RestAssured.given()
            .accept("application/json")
            .`when`()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .body("components.schemas.VersionData.properties", hasKey("major"))
            .body("components.schemas.VersionData.properties", hasKey("additional_version_numbers"))
            .body("components.schemas.VersionData.properties", not(hasKey("version_numbers")))
    }

    @Test
    fun parsesNoVersion() {

        RestAssured.given()
            .`when`()
            .get("/v3/version/")
            .then()
            .statusCode(404)
    }

    @Test
    fun parsesBadVersion() {

        RestAssured.given()
            .`when`()
            .get("/v3/version/fooBar")
            .then()
            .statusCode(400)
    }

    @Test
    fun supportsPageSize() {

        val parsed = VersionParser.parse("jdk-11.0.5+10")

        RestAssured.given()
            .`when`()
            .get("/v3/version/jdk-11.0.5+10?page_size=50")
            .then()
            .statusCode(200)
            .body(VersionDataMatcher(parsed))
    }
}
