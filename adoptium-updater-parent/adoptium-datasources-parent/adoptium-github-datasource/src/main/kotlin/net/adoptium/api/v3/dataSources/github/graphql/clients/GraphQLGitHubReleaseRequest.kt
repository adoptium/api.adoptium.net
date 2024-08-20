package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.ReleaseQueryData
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

@ApplicationScoped
open class GraphQLGitHubReleaseRequest @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface
) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getAllReleaseAssets(release: GHRelease): GHRelease {
        val query = GetMoreReleaseAssetsQuery(release.id)

        LOGGER.debug("Getting release assets ${release.id.id}")
        val moreAssets = graphQLGitHubInterface.getAll(
            query::withCursor,
            { asset ->
                if (asset.assetNode == null) listOf()
                else asset.assetNode.releaseAssets.assets
            },
            { it.assetNode!!.releaseAssets.pageInfo.hasNextPage },
            { it.assetNode!!.releaseAssets.pageInfo.endCursor },
            release.releaseAssets.pageInfo.endCursor,
            null
        )

        val assets = release.releaseAssets.assets.union(moreAssets)

        return GHRelease(
            release.id,
            release.name,
            release.isPrerelease,
            release.publishedAt,
            release.updatedAt,
            GHAssets(
                assets.toList(),
                PageInfo(false, null),
                0
            ),
            release.resourcePath,
            release.url
        )
    }

    private class GetMoreReleaseAssetsQuery(private val releaseId: GitHubId, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<ReleaseQueryData> {

        fun withCursor(cursor: String?): GetMoreReleaseAssetsQuery {
            return if (cursor != null) GetMoreReleaseAssetsQuery(releaseId, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String
            get() =
                """query(${'$'}cursorPointer:String) {
                              node(id:"${releaseId.id}") {
                                ... on Release {
                                    releaseAssets(first:50, after:${'$'}cursorPointer) {
                                        nodes {
                                            downloadCount,
                                            updatedAt,
                                            name,
                                            downloadUrl,
                                            size
                                        },
                                        pageInfo {
                                            hasNextPage,
                                            endCursor
                                        }
                                    }
                                }
                              }
                              rateLimit {
                                cost,
                                remaining
                              }
                            }
                    """
                    .trimIndent()
                    .replace("\n", "")

        override fun responseType(): KClass<ReleaseQueryData> {
            return ReleaseQueryData::class
        }
    }
}
