package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse

interface GraphQLRequest {
    suspend fun <T : Any> request(query: GraphQLClientRequest<T>): GraphQLClientResponse<T>
}

