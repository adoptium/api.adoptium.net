package net.adoptium.api.v3

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

object JsonMapper {
    val mapper: ObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerModule(JavaTimeModule())
}
