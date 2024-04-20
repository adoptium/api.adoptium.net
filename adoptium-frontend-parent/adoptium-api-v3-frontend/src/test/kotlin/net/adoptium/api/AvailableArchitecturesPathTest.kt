package net.adoptium.api

import io.restassured.RestAssured
import io.restassured.config.RedirectConfig
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Architecture
import org.junit.jupiter.api.Test

class AvailableArchitecturesPathTest : FrontendTest() {

    @Test
    fun availableArchitectures() {
        RestAssured.given()
            .`when`()
            .get("/v3/info/available/architectures")
            .then()
            .statusCode(200)
    }

    @Test
    fun availableArchitecturesAreCorrect() {
        var body = RestAssured.given()
            .`when`()
            .get("/v3/info/available/architectures")
            .body

        val architectures = parseArchitectures(body.asString())

        assert(architectures.contains(Architecture.x64.name))
        assert(architectures.size == Architecture.values().size)
    }

    private fun parseArchitectures(json: String?): List<String> =
        JsonMapper.mapper.readValue(json, JsonMapper.mapper.typeFactory.constructCollectionType(MutableList::class.java, String::class.java))
}
