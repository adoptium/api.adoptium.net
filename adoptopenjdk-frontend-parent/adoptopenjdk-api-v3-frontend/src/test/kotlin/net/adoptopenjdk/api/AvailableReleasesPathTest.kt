package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.models.ReleaseInfo
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Test

@QuarkusTest
class AvailableReleasesPathTest : FrontendTest() {

    @Test
    fun availableReleases() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/available_releases")
            .then()
            .statusCode(200)
    }

    @Test
    fun availableVersionsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.available_releases.contentEquals(arrayOf(8, 9, 10, 11, 12))
        }
    }

    @Test
    fun availableLtsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.available_lts_releases.contentEquals(arrayOf(8, 11))
        }
    }

    @Test
    fun mostRecentLtsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_lts == 11
        }
    }

    @Test
    fun mostRecentFeatureReleaseIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_feature_release == 12
        }
    }

    @Test
    fun mostRecentFeatureVersionIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_feature_version == 13
        }
    }

    private fun check(matcher: (ReleaseInfo) -> Boolean) {
        RestAssured.given()
            .`when`()
            .get("/v3/info/available_releases")
            .then()
            .body(object : TypeSafeMatcher<String>() {

                override fun describeTo(description: Description?) {
                    description!!.appendText("json")
                }

                override fun matchesSafely(p0: String?): Boolean {
                    val releaseInfo = JsonMapper.mapper.readValue(p0, ReleaseInfo::class.java)
                    return matcher(releaseInfo)
                }
            })
    }
}
