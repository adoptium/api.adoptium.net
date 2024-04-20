package net.adoptium.api

import io.restassured.RestAssured
import io.restassured.config.RedirectConfig
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.OperatingSystem
import org.junit.jupiter.api.Test

class AvailableOperatingSystemsPathTest : FrontendTest() {

    @Test
    fun availableOperatingSystems_deprecated() {
        RestAssured.given()
            .config(RestAssured.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)))
            .`when`()
            .get("/v3/info/available_operating-systems")
            .then()
            .statusCode(301)
    }

    @Test
    fun availableOperatingSystems() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/available/operating-systems")
            .then()
            .statusCode(200)
    }

    @Test
    fun availableOperatingSystemsAreCorrect() {
        var body = RestAssured.given()
            .`when`()
            .get("/v3/info/available/operating-systems")
            .body

        val operatingSystems = parseOperatingSystems(body.asString())

        assert(operatingSystems.contains(OperatingSystem.linux.name))
        assert(operatingSystems.size == OperatingSystem.values().size)
    }

    private fun parseOperatingSystems(json: String?): List<String> =
        JsonMapper.mapper.readValue(json, JsonMapper.mapper.typeFactory.constructCollectionType(MutableList::class.java, String::class.java))
}
