package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import jakarta.enterprise.context.ApplicationScoped
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.GitHubAuth
import java.net.URL

@ApplicationScoped
open class GraphQLRequestImpl : GraphQLRequest {

    private val client: GraphQLKtorClient
    private val httpClient: HttpClient
    val BASE_URL = "https://api.github.com/graphql"
    private val TOKEN: String

    init {
        val token = GitHubAuth.readToken()
        if (token == null) {
            throw IllegalStateException("No token provided")
        } else {
            TOKEN = token
        }
        httpClient = HttpClient()
        client = GraphQLKtorClient(
            url = URL(BASE_URL),
            httpClient = httpClient,
            serializer = GraphQLClientJacksonSerializer(UpdaterJsonMapper.mapper)
        )
    }

    override suspend fun <T : Any> request(query: GraphQLClientRequest<T>): GraphQLClientResponse<T> {
        return client.execute(query) {
            headers.append("Authorization", "Bearer $TOKEN")
        }
    }
}
