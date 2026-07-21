package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.serializer.GraphQLClientSerializer
import com.expediagroup.graphql.client.types.GraphQLClientError
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import com.expediagroup.graphql.client.types.GraphQLClientSourceLocation
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.KClass

/**
 * Custom GraphQL client serializer that uses Jackson 2.x (com.fasterxml.jackson) so that the
 * project's existing model classes and ObjectMapper configuration are reused without requiring
 * a migration to Jackson 3.x (tools.jackson), which is pulled in by graphql-kotlin-client-jackson 10.x.
 */
class JacksonGraphQLClientSerializer(private val mapper: ObjectMapper) : GraphQLClientSerializer {

    override fun serialize(request: GraphQLClientRequest<*>): String {
        val body = mutableMapOf<String, Any?>()
        request.query?.let { body["query"] = it }
        request.operationName?.let { body["operationName"] = it }
        request.variables?.let { body["variables"] = it }
        request.extensions?.let { if (it.isNotEmpty()) body["extensions"] = it }
        return mapper.writeValueAsString(body)
    }

    override fun serialize(requests: List<GraphQLClientRequest<*>>): String {
        return mapper.writeValueAsString(requests.map { request ->
            val body = mutableMapOf<String, Any?>()
            request.query?.let { body["query"] = it }
            request.operationName?.let { body["operationName"] = it }
            request.variables?.let { body["variables"] = it }
            request.extensions?.let { if (it.isNotEmpty()) body["extensions"] = it }
            body
        })
    }

    override fun <T : Any> deserialize(rawResponse: String, responseType: KClass<T>): GraphQLClientResponse<T> {
        val rawResult = mapper.readValue(rawResponse, RawGraphQLResponse::class.java)
        val data = rawResult.data?.let { mapper.treeToValue(it, responseType.java) }
        return GraphQLClientResponseImpl(
            data = data,
            errors = rawResult.errors?.map { err ->
                GraphQLClientErrorImpl(
                    message = err.message,
                    locations = err.locations,
                    path = err.path,
                    extensions = err.extensions
                )
            }
        )
    }

    override fun deserialize(rawResponse: String, responseTypes: List<KClass<*>>): List<GraphQLClientResponse<*>> {
        val listType = mapper.typeFactory.constructCollectionType(List::class.java, RawGraphQLResponse::class.java)
        val rawResults: List<RawGraphQLResponse> = mapper.readValue(rawResponse, listType)
        return rawResults.zip(responseTypes) { rawResult, responseType ->
            val data = rawResult.data?.let { mapper.treeToValue(it, responseType.java) }
            GraphQLClientResponseImpl(
                data = data,
                errors = rawResult.errors?.map { err ->
                    GraphQLClientErrorImpl(
                        message = err.message,
                        locations = err.locations,
                        path = err.path,
                        extensions = err.extensions
                    )
                }
            )
        }
    }

    private data class RawGraphQLResponse(
        @JsonProperty("data") val data: JsonNode? = null,
        @JsonProperty("errors") val errors: List<RawGraphQLError>? = null
    )

    private data class RawGraphQLError @JsonCreator constructor(
        @JsonProperty("message") val message: String,
        @JsonProperty("locations") val locations: List<SourceLocationImpl>? = null,
        @JsonProperty("path") val path: List<Any>? = null,
        @JsonProperty("extensions") val extensions: Map<String, Any>? = null
    )

    private data class SourceLocationImpl @JsonCreator constructor(
        @JsonProperty("line") override val line: Int,
        @JsonProperty("column") override val column: Int
    ) : GraphQLClientSourceLocation

    private data class GraphQLClientErrorImpl(
        override val message: String,
        override val locations: List<GraphQLClientSourceLocation>? = null,
        override val path: List<Any>? = null,
        override val extensions: Map<String, Any>? = null
    ) : GraphQLClientError

    private data class GraphQLClientResponseImpl<T>(
        override val data: T? = null,
        override val errors: List<GraphQLClientError>? = null,
        override val extensions: Map<String, Any>? = null
    ) : GraphQLClientResponse<T>
}
