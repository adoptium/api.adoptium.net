package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import jakarta.enterprise.context.ApplicationScoped
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.GitHubAuth
import net.adoptium.api.v3.dataSources.github.GitHubAuth.AuthInfo
import java.net.URL
import org.slf4j.LoggerFactory
import net.adoptium.api.v3.config.APIConfig

@ApplicationScoped
open class GraphQLRequestImpl : GraphQLRequest {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val client: GraphQLKtorClient

    private val httpClient: HttpClient = if (APIConfig.DEBUG) {
                                             HttpClient {
                                                 install(io.ktor.client.plugins.logging.Logging) {
                                                     level = io.ktor.client.plugins.logging.LogLevel.ALL
                                                     logger = io.ktor.client.plugins.logging.Logger.DEFAULT
                                                 }
                                             }
                                         } else { HttpClient() }

    val BASE_URL = "https://api.github.com/graphql"

    init {
        client = GraphQLKtorClient(
            url = URL(BASE_URL),
            httpClient = httpClient,
            serializer = GraphQLClientJacksonSerializer(UpdaterJsonMapper.mapper)
        )
    }

    override suspend fun <T : Any> request(query: GraphQLClientRequest<T>): GraphQLClientResponse<T> {
        val authInfo: AuthInfo = GitHubAuth.getAuthenticationToken()
        return client.execute(query) {
            headers.append("Authorization", "Bearer ${authInfo.token}")
        }
    }
}
