package net.adoptopenjdk.api.v3.dataSources.github.graphql.clients

import io.aexp.nodes.graphql.GraphQLRequestEntity
import net.adoptopenjdk.api.v3.dataSources.github.GitHubAuth
import javax.inject.Singleton

interface GraphQLRequestFactory {
    fun getRequestBuilder(): GraphQLRequestEntity.RequestBuilder
}

@Singleton
class GraphQLRequestFactoryImpl : GraphQLRequestFactory {

    private val BASE_URL = "https://api.github.com/graphql"
    private val TOKEN: String

    constructor() {
        val token = GitHubAuth.readToken()
        if (token == null) {
            throw IllegalStateException("No token provided")
        } else {
            TOKEN = token
        }
    }

    override fun getRequestBuilder(): GraphQLRequestEntity.RequestBuilder {
        return GraphQLRequestEntity.Builder()
            .url(BASE_URL)
            .headers(
                mapOf(
                    "Authorization" to "Bearer $TOKEN"
                )
            )
    }
}
