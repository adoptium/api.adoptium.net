package net.adoptopenjdk.api.v3.dataSources.github.graphql.clients

import io.aexp.nodes.graphql.GraphQLRequestEntity
import io.aexp.nodes.graphql.GraphQLResponseEntity
import io.aexp.nodes.graphql.GraphQLTemplate
import javax.inject.Singleton

interface GraphQLRequest {
    fun <F> query(query: GraphQLRequestEntity, clazz: Class<F>): GraphQLResponseEntity<F>
}

@Singleton
class GraphQLRequestImpl : GraphQLRequest {
    override fun <F> query(query: GraphQLRequestEntity, clazz: Class<F>): GraphQLResponseEntity<F> {
        return GraphQLTemplate(Int.MAX_VALUE, Int.MAX_VALUE).query(query, clazz)
    }
}
