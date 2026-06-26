package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.serializer.GraphQLClientSerializer
import com.expediagroup.graphql.client.types.GraphQLClientError
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import com.expediagroup.graphql.client.types.GraphQLClientSourceLocation
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.reflect.KClass

class LegacyJacksonGraphQLSerializer(
    private val mapper: ObjectMapper
) : GraphQLClientSerializer {

    override fun serialize(request: GraphQLClientRequest<*>): String {
        return mapper.writeValueAsString(request)
    }

    override fun serialize(requests: List<GraphQLClientRequest<*>>): String {
        return mapper.writeValueAsString(requests)
    }

    override fun <T : Any> deserialize(rawResponse: String, responseType: KClass<T>): GraphQLClientResponse<T> {
        return deserializeNode(mapper.readTree(rawResponse), responseType)
    }

    override fun deserialize(rawResponses: String, responseTypes: List<KClass<*>>): List<GraphQLClientResponse<*>> {
        val rootNode = mapper.readTree(rawResponses)
        return if (rootNode.isArray) {
            rootNode.mapIndexed { index, node -> deserializeNode(node, responseTypes[index]) }
        } else {
            listOf(deserializeNode(rootNode, responseTypes.first()))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> deserializeNode(node: JsonNode, responseType: KClass<T>): GraphQLClientResponse<T> {
        val data = node.get("data")
            ?.takeUnless { it.isNull }
            ?.let { mapper.treeToValue(it, responseType.java) }
        val errors = node.get("errors")
            ?.takeUnless { it.isNull }
            ?.let {
                mapper.convertValue(
                    it,
                    mapper.typeFactory.constructCollectionType(List::class.java, LegacyGraphQLClientError::class.java)
                ) as List<LegacyGraphQLClientError>
            }
        val extensions = node.get("extensions")
            ?.takeUnless { it.isNull }
            ?.let {
                mapper.convertValue(
                    it,
                    mapper.typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)
                ) as Map<String, Any>
            }
        return LegacyGraphQLClientResponse(data, errors, extensions)
    }
}

data class LegacyGraphQLClientResponse<T>(
    override val data: T?,
    override val errors: List<LegacyGraphQLClientError>?,
    override val extensions: Map<String, Any>?
) : GraphQLClientResponse<T>

data class LegacyGraphQLClientError(
    override val message: String,
    override val locations: List<LegacyGraphQLClientSourceLocation>? = null,
    override val path: List<Any>? = null,
    override val extensions: Map<String, Any>? = null
) : GraphQLClientError

data class LegacyGraphQLClientSourceLocation(
    override val line: Int,
    override val column: Int
) : GraphQLClientSourceLocation
