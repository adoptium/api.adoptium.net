package net.adoptium.api.v3.routes.info

import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.models.ReleaseInfo
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo

@Tag(name = "Release Info")
@Path("/v3/info")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AvailableReleasesResource
@Inject
constructor(
    private val apiDataStore: APIDataStore
) {

    @GET
    @Path("/available_releases")
    @Deprecated("Use the new get() method with new path /available/releases")
    fun get301(uriInfo: UriInfo): Response {
        val location = uriInfo.requestUriBuilder.replacePath("/v3/info/available/releases").build()
        return Response
            .status(Response.Status.MOVED_PERMANENTLY)
            .location(location)
            .entity(apiDataStore.getReleaseInfo())
            .build()
    }

    @GET
    @Path("/available/releases")
    @Operation(summary = "Returns information about available releases", operationId = "getAvailableReleases")
    fun get(): ReleaseInfo {
        return apiDataStore.getReleaseInfo()
    }
}
