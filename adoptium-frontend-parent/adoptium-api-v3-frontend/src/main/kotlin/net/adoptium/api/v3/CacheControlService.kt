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
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*


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

    private fun calculateEtag(requestContext: ContainerRequestContext): EntityTag {
        val md = MessageDigest.getInstance("SHA1")
        try {
            md.update(Base64.getDecoder().decode(apiDataStore.getUpdateInfo().checksum))
        } catch (e: Exception) {
            // Should not happen as the hex checksum should always be a valid Base64 string
            md.update(apiDataStore.getUpdateInfo().checksum.toByteArray())
        }
        md.update(requestContext.uriInfo.requestUri.toString().toByteArray())

        return EntityTag(BigInteger(1, md.digest()).toString(16))
    }

    override fun filter(requestContext: ContainerRequestContext?) {
        if (isCacheControlledPath(requestContext)) {
            val etag = calculateEtag(requestContext!!)

            val lastModified = apiDataStore.getUpdateInfo().lastModified

            if (lastModified == null) {
                return
            }

            val builder =
                requestContext
                    .request
                    .evaluatePreconditions(lastModified, etag)

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

            if (apiDataStore.getUpdateInfo().lastModifiedFormatted == null) {
                return
            }

            val etag = calculateEtag(requestContext!!)

            if (responseContext?.headers?.containsKey("ETag") == false) {
                responseContext.headers?.add("ETag", etag)
            }
            responseContext?.headers?.add("Last-Modified", apiDataStore.getUpdateInfo().lastModifiedFormatted)
            responseContext?.headers?.add("Cache-Control", CacheControlDelegate.INSTANCE.toString(ecc))
        }
    }
}
