package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.OperatingSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class TypesOperatingSystemsPathTest : FrontendTest() {

    @Test
    fun getOperatingSystems() {
        RestAssured.given()
            .`when`()
            .get("/v3/types/operating_systems")
            .then()
            .statusCode(200)
    }

    @Test
    fun getOperatingSystemsAreCorrect() {
        val body = RestAssured.given()
            .`when`()
            .get("/v3/types/operating_systems")
            .body

        val operatingSystems = parseOperatingSystems(body.asString())

        assert(operatingSystems.contains(OperatingSystem.linux.name))
        assert(operatingSystems.size == OperatingSystem.entries.size)
    }

    private fun parseOperatingSystems(json: String?): List<String> =
        JsonMapper.mapper.readValue(json, JsonMapper.mapper.typeFactory.constructCollectionType(MutableList::class.java, String::class.java))
}
