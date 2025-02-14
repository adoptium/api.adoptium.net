package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Architecture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class TypesArchitecturesPathTest : FrontendTest() {

    @Test
    fun getArchitectures() {
        RestAssured.given()
            .`when`()
            .get("/v3/types/architectures")
            .then()
            .statusCode(200)
    }

    @Test
    fun getArchitecturesAreCorrect() {
        val body = RestAssured.given()
            .`when`()
            .get("/v3/types/architectures")
            .body

        val architectures = parseArchitectures(body.asString())

        assert(architectures.contains(Architecture.x64.name))
        assert(architectures.size == Architecture.entries.size)
    }

    private fun parseArchitectures(json: String?): List<String> =
        JsonMapper.mapper.readValue(json, JsonMapper.mapper.typeFactory.constructCollectionType(MutableList::class.java, String::class.java))
}
