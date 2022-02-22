package net.adoptium.marketplace.server.frontend.routes.info

import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.dataSources.ReleaseInfo
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.server.frontend.OpenApiDocs
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Tag(name = "Release Info")
@Path("/v1/info/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AvailableReleasesResource
@Inject
constructor(
    private val apiDataStore: APIDataStore
) {
    @GET
    @Path("/available_releases/{vendor}")
    @Operation(summary = "Returns information about available releases", operationId = "getAvailableReleases")
    suspend fun get(
        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor
    ): ReleaseInfo {
        return apiDataStore.getReleases(vendor).getReleaseInfo()
    }
}
