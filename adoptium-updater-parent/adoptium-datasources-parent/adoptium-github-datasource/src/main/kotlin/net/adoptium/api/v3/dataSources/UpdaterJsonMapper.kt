package net.adoptium.api.v3.dataSources

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsonp.JSONPModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jakarta.ws.rs.Produces
import jakarta.ws.rs.ext.Provider

@Provider
class UpdaterJsonMapper {
    companion object {
        val mapper: ObjectMapper = ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .registerModule(JSONPModule())
    }

    @Produces
    fun getObjectMapper(): ObjectMapper {
        return mapper
    }
}
