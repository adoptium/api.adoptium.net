package net.adoptium.api.v3

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

object XmlMapper {

    val mapper: ObjectMapper = XmlMapper.builder()
        .defaultUseWrapper(false)
        .addModule(KotlinModule.Builder().disable(KotlinFeature.StrictNullChecks).build())
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
}
