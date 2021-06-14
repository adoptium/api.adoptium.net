package net.adoptopenjdk.api.v3.dataSources

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr353.JSR353Module
import com.fasterxml.jackson.module.kotlin.KotlinModule

object UpdaterJsonMapper {
    val mapper: ObjectMapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .registerModule(JSR353Module())
}
