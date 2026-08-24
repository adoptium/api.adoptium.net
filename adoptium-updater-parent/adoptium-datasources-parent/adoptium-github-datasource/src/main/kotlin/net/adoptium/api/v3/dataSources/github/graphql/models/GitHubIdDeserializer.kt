package net.adoptium.api.v3.dataSources.github.graphql.models

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import net.adoptium.api.v3.dataSources.models.GitHubId

object GitHubIdDeserializer : ValueDeserializer<GitHubId>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): GitHubId {
        return GitHubId(parser.valueAsString)
    }
}
