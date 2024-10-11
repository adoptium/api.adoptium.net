package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.core.EntityTag
import jakarta.ws.rs.ext.Provider
import net.adoptium.api.v3.dataSources.APIDataStore
import org.jboss.resteasy.reactive.common.headers.CacheControlDelegate
import org.jboss.resteasy.reactive.common.util.ExtendedCacheControl
import org.jboss.resteasy.reactive.server.ServerResponseFilter

@Provider
@ApplicationScoped
class CacheControlService @Inject constructor(private var apiDataStore: APIDataStore) : ContainerRequestFilter {

    private val CACHE_CONTROLLED_PATHS = listOf("/v3/info", "/v3/assets")

    override fun filter(requestContext: ContainerRequestContext?) {
        if (requestContext == null) return

        requestContext.uriInfo?.path?.let { path ->
            if (CACHE_CONTROLLED_PATHS.any { path.startsWith(it) }) {
                val etag = apiDataStore.getUpdateInfo().hexChecksum
                val lastModified = apiDataStore.getUpdateInfo().lastModified

                val builder =
                    requestContext
                        .request
                        .evaluatePreconditions(lastModified, EntityTag(etag))

                if (builder != null) {
                    requestContext.abortWith(builder.build())
                }
            }
        }
    }

    @ServerResponseFilter
    fun responseFilter(responseContext: ContainerResponseContext?) {
        val ecc = ExtendedCacheControl();
        ecc.isPublic = true

        responseContext?.headers?.add("ETag", apiDataStore.getUpdateInfo().hexChecksum)
        responseContext?.headers?.add("Last-Modified", apiDataStore.getUpdateInfo().lastModifiedFormatted)
        responseContext?.headers?.add("Cache-Control", CacheControlDelegate.INSTANCE.toString(ecc))
    }

}
