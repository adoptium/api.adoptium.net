package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.core.EntityTag
import jakarta.ws.rs.core.Request
import jakarta.ws.rs.ext.Provider
import net.adoptium.api.v3.dataSources.APIDataStore
import org.jboss.resteasy.reactive.common.headers.CacheControlDelegate
import org.jboss.resteasy.reactive.common.util.ExtendedCacheControl
import org.jboss.resteasy.reactive.server.ServerResponseFilter


@Provider
@ApplicationScoped
class CacheControlService @Inject constructor(private var apiDataStore: APIDataStore) : ContainerRequestFilter {

    private val CACHE_CONTROLLED_PATHS = listOf("/v3/info", "/v3/assets")
    private val MAX_CACHE_AGE_IN_SEC = 120

    private fun isCacheControlledPath(requestContext: ContainerRequestContext?): Boolean {
        val path = requestContext?.uriInfo?.path

        if (path == null) return false

        return CACHE_CONTROLLED_PATHS.any { path.startsWith(it) }
    }

    override fun filter(requestContext: ContainerRequestContext?) {
        if (isCacheControlledPath(requestContext)) {
            val etag = apiDataStore.getUpdateInfo().hexChecksum
            val lastModified = apiDataStore.getUpdateInfo().lastModified

            if (lastModified == null || etag == null) {
                return
            }

            val builder =
                requestContext!!
                    .request
                    .evaluatePreconditions(lastModified, EntityTag(etag))

            if (builder != null) {
                requestContext.abortWith(builder.build())
            }
        }
    }

    @ServerResponseFilter
    fun responseFilter(requestContext: ContainerRequestContext?, responseContext: ContainerResponseContext?) {
        if (isCacheControlledPath(requestContext)) {
            val ecc = ExtendedCacheControl();
            ecc.isPublic = true
            ecc.maxAge = MAX_CACHE_AGE_IN_SEC
            ecc.sMaxAge = MAX_CACHE_AGE_IN_SEC

            if (apiDataStore.getUpdateInfo().hexChecksum == null ||
                apiDataStore.getUpdateInfo().lastModifiedFormatted == null) {
                return
            }

            responseContext?.headers?.add("ETag", apiDataStore.getUpdateInfo().hexChecksum)
            responseContext?.headers?.add("Last-Modified", apiDataStore.getUpdateInfo().lastModifiedFormatted)
            responseContext?.headers?.add("Cache-Control", CacheControlDelegate.INSTANCE.toString(ecc))
        }
    }
}
