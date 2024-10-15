package net.adoptium.api.v3.routes.info

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.models.ReleaseInfo
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag

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
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200",
                description = "Available release information",
                content = [Content(schema = Schema(type = SchemaType.OBJECT, implementation = ReleaseInfo::class))]
            )
        ]
    )
    @Operation(summary = "Returns information about available releases", operationId = "getAvailableReleases")
    fun get(): ReleaseInfo {
        return apiDataStore.getReleaseInfo()
    }
}
