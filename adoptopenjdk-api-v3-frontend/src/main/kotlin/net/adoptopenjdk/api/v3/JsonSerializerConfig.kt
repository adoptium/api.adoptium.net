package net.adoptopenjdk.api.v3

import io.quarkus.jsonb.JsonbConfigCustomizer
import javax.inject.Singleton
import javax.json.bind.JsonbConfig

@Singleton
class JsonSerializerConfig : JsonbConfigCustomizer {
    override fun customize(config: JsonbConfig) {
        config.withFormatting(true)
    }
}
