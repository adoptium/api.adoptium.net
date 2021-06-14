package net.adoptopenjdk.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId

object GitHubIdDeserializer : JsonDeserializer<GitHubId>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext?): GitHubId {
        return GitHubId(parser.valueAsString)
    }
}
