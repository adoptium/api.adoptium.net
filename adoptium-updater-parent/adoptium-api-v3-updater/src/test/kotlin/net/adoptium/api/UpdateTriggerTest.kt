package net.adoptium.api

import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verify
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptium.api.v3.Updater
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.models.Release
import org.jboss.weld.junit5.auto.EnableAlternatives
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.Before
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.annotation.Priority
import javax.enterprise.inject.Alternative
import javax.inject.Singleton
import javax.ws.rs.core.Response

@Priority(1)
@Alternative
@Singleton
class MockUpdater : Updater {
    companion object {
        var spied: Updater = spyk(object : Updater {
            override fun addToUpdate(toUpdate: String): List<Release> {
                return BaseTest.adoptRepos.allReleases.getReleases()
                    .filter {
                        it.release_name == toUpdate
                    }.toList()
            }
        })
    }

    override fun addToUpdate(toUpdate: String): List<Release> {
        return spied.addToUpdate(toUpdate)
    }
}

@QuarkusTest
@EnableAlternatives
@EnableAutoWeld
class UpdateTriggerTest : BaseTest() {

    companion object {
        init {
            APIConfig.DISABLE_UPDATER = true
        }
    }

    @Before
    fun setUp() = MockKAnnotations.init(this, relaxUnitFun = true)

    @BeforeEach
    fun beforeEach() {
        clearMocks(MockUpdater.spied)
    }

    @Test
    fun passesOnReleaseName() {
        getRequest()
            .`when`()
            .get("/updater/${getReleaseName()}")
            .then()
            .statusCode(Response.Status.OK.statusCode)
        verify(exactly = 1) { MockUpdater.spied.addToUpdate(getReleaseName()) }
        confirmVerified(MockUpdater.spied)
    }

    private fun getReleaseName() = adoptRepos.allReleases.getReleases().first().release_name

    @Test
    fun failsOnNoToken() {
        RestAssured.given()
            .`when`()
            .get("/updater/bar")
            .then()
            .statusCode(Response.Status.UNAUTHORIZED.statusCode)
        verify(exactly = 0) { MockUpdater.spied.addToUpdate("bar") }
        confirmVerified(MockUpdater.spied)
    }

    @Test
    fun failsOnBadToken() {
        RestAssured.given()
            .auth()
            .preemptive()
            .basic("testUpdaterUser", "a-bad-token")
            .`when`()
            .get("/updater/bar")
            .then()
            .statusCode(Response.Status.UNAUTHORIZED.statusCode)
        verify(exactly = 0) { MockUpdater.spied.addToUpdate("bar") }
        confirmVerified(MockUpdater.spied)
    }

    @Test
    fun failsOnBadRelease() {
        getRequest()
            .get("/updater/bar")
            .then()
            .statusCode(Response.Status.NOT_FOUND.statusCode)
        verify(exactly = 1) { MockUpdater.spied.addToUpdate("bar") }
        confirmVerified(MockUpdater.spied)
    }

    private fun getRequest() = RestAssured.given()
        .auth()
        .preemptive()
        .basic("testUpdaterUser", "a-test-token")
        .`when`()
}
