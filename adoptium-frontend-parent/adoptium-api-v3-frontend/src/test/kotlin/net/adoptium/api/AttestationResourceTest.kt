package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.dataSources.APIDataStore
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class AttestationResourceTest : FrontendTest() {

    @Inject
    lateinit var apiDataStore: APIDataStore

    @Test
    fun `find existing attestation`() {
        RestAssured.given()
            .`when`()
            .get("/v3/attestations/version/jdk-21.0.5+6/linux/x64/jdk/hotspot/eclipse/123456abcdef")
            .then()
            .statusCode(200)
    }

    @Test
    fun `non-existent attestation 404`() {
        RestAssured.given()
            .`when`()
            .get("/v3/attestations/version/foo/linux/x64/jdk/hotspot/eclipse/123456")
            .then()
            .statusCode(404)
    }
}
