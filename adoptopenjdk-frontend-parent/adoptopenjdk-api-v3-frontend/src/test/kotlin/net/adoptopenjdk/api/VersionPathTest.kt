package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.models.VersionData
import net.adoptopenjdk.api.v3.parser.VersionParser
import org.hamcrest.Description
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
