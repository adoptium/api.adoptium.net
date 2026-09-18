package net.adoptium.api.v3

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder

object JsonMapper {
    val mapper: JsonMapper = jacksonMapperBuilder {
        disable(KotlinFeature.StrictNullChecks)
    }
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        .build()
}
