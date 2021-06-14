package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertTrue

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class JsonSerializationTest : FrontendTest() {

    class PrettyPrintMatcher : TypeSafeMatcher<String>() {

        override fun describeTo(description: Description?) {
            description!!.appendText("json")
        }

        override fun matchesSafely(p0: String?): Boolean {
            assertTrue(p0!!.contains("\n"))
            return true
        }
    }

    @Test
    fun isPrettyPrinted() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/available_releases")
            .then()
            .statusCode(200)
            .body(PrettyPrintMatcher())
    }
}
