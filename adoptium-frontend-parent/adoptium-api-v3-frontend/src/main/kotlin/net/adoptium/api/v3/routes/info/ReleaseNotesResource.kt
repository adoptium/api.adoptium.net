package net.adoptium.api.v3.routes.info

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.adoptium.api.v3.OpenApiDocs
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.Vendor
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Tag(name = "Release Info")
@Path("/v3/info")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class ReleaseNotesResource
@Inject
constructor(
    private val apiPersistence: ApiPersistence
) {
    @GET
    @Path("/release_notes/{release_name}")
    @Operation(summary = "Returns release notes for a release version", operationId = "getReleaseNotes")
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "Release notes for the provided release",
                content = [Content(schema = Schema(type = SchemaType.OBJECT, implementation = ReleaseNotes::class))]
            )
        ]
    )
    fun getReleaseNotes(
        @Parameter(
            name = "release_name", description = OpenApiDocs.RELASE_NAME, required = true,
            schema = Schema(example = "jdk-19.0.2+7", type = SchemaType.STRING)
        )
        @PathParam("release_name")
        release_name: String,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = false)
        @QueryParam("vendor")
        vendor: Vendor?,

        ): CompletionStage<Response> {
        return runAsync {
            if (release_name == null) {
                return@runAsync null
            }

            val vendorNonNull = vendor ?: Vendor.getDefault()

            return@runAsync apiPersistence.getReleaseNotes(vendorNonNull, release_name)
        }
    }

    private inline fun <reified T> runAsync(crossinline doIt: suspend () -> T): CompletionStage<Response> {
        val future = CompletableFuture<Response>()
        GlobalScope.launch {
            try {
                val result = doIt()
                if (result == null) {
                    future.complete(Response.status(404).build())
                } else {
                    future.complete(Response.ok(doIt()).build())
                }
            } catch (e: BadRequestException) {
                future.complete(Response.status(400).entity("Bad request").build())
            } catch (e: Exception) {
                future.complete(Response.status(500).entity("Internal error").build())
            }
        }

        return future
    }
}
