package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.response.Response
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
import org.junit.jupiter.api.Assertions.assertEquals

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class AttestationResourceTest : FrontendTest() {

    @Inject
    lateinit var apiDataStore: APIDataStore

    @Test
    fun `find existing attestations`() {
        val response: Response = RestAssured.given()
            .`when`()
            .get("/v3/attestations/release_name/jdk-21.0.5+6/linux/x64/jdk/hotspot/eclipse")

        val release_names: List<String> = response.jsonPath().getList("release_name")

        // There should be 2 Attestations in the generated test data for jdk-21.0.5+6
        assertEquals(2, release_names.size, "Expected 2 Attestations in the response for jdk-21.0.5+6")
    }

    @Test
    fun `find existing attestations by target_checksum`() {
        val response: Response = RestAssured.given()
            .`when`()
            .get("/v3/attestations/target_checksum/123456ABcdEF")

        val release_names: List<String> = response.jsonPath().getList("release_name")

        // There should be 2 Attestations in the generated test data for checksum 123456abcdef
        assertEquals(2, release_names.size, "Expected 2 Attestations in the response for target_checksum 123456abcdef")
    }

    @Test
    fun `find existing attestations by release_name`() {
        val response: Response = RestAssured.given()
            .`when`()
            .get("/v3/attestations/release_name/jdk-24.0.2+12")

        val release_names: List<String> = response.jsonPath().getList("release_name")

        // There should be 1 Attestation in the generated test data for version jdk-24.0.2+12
        assertEquals(1, release_names.size, "Expected 1 Attestation in the response for version jdk-24.0.2+12")
    }

    @Test
    fun `non-existent attestation 404`() {
        RestAssured.given()
            .`when`()
            .get("/v3/attestations/release_name/foo/linux/x64/jdk/hotspot/eclipse")
            .then()
            .statusCode(404)
    }

    @Test
    fun `non-existent attestations by target_checksum`() {
        RestAssured.given()
            .`when`()
            .get("/v3/attestations/target_checksum/654321")
            .then()
            .statusCode(404)
    }

    @Test
    fun `non-existent attestations by release_name`() {
        RestAssured.given()
            .`when`()
            .get("/v3/attestations/release_name/jdk-99+36")
            .then()
            .statusCode(404)
    }
}
